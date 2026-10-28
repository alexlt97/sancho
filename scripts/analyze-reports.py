#!/usr/bin/env python3
"""Analyze a Sancho report ZIP using only the Python standard library."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path


REQUIRED_FIELDS = {"schema_version", "id", "category", "title", "description"}


def classify_priority(report: dict) -> tuple[str, str]:
    text = " ".join(
        str(report.get(field, ""))
        for field in ("title", "description", "actual_behavior")
    ).lower()
    if re.search(r"security|credential|private key|data loss|leak|remote code", text):
        return "P0", "Possible security or data-loss impact."
    if re.search(r"crash|does not start|cannot use|unusable|corrupt|no response", text):
        return "P1", "The report may block a core workflow or indicate instability."
    if report.get("category") == "Missing feature":
        return "P2", "A requested capability is missing but is not described as blocking."
    if re.search(r"layout|spacing|color|label|font|button|preview|text", text):
        return "P3", "The report appears low impact and limited to presentation."
    return "P2", "The issue affects behavior but no critical impact signal was detected."


def classify_risk(report: dict) -> tuple[str, str]:
    text = " ".join(
        str(report.get(field, ""))
        for field in ("title", "description", "reproduction_steps", "actual_behavior")
    ).lower()
    if re.search(r"network|permission|credential|security|ssh|shell|remote|migration", text):
        return "High", "Implementation may affect security, permissions, or persistent data."
    if re.search(r"markdown|table|equation|latex|math", text):
        return "Medium", "Implementation changes the Markdown rendering path and needs regression coverage."
    if re.search(r"model|inference|image|performance|storage|persist", text):
        return "Medium", "Implementation may cross model, storage, or device-runtime boundaries."
    return "Low", "The report appears limited to presentation or local behavior."


def fingerprint(report: dict) -> str:
    fields = (
        report.get("category", ""),
        report.get("title", "").strip().lower(),
        report.get("description", "").strip().lower(),
    )
    return hashlib.sha256("\n".join(fields).encode()).hexdigest()[:12]


def affected_area(report: dict) -> str:
    text = " ".join(
        str(report.get(field, ""))
        for field in (
            "title",
            "description",
            "reproduction_steps",
            "expected_behavior",
            "actual_behavior",
        )
    ).lower()
    for keyword, area in (
        ("equation", "Markdown math"),
        ("latex", "Markdown math"),
        ("math", "Markdown math"),
        ("table", "Markdown tables"),
        ("markdown", "Markdown rendering"),
        ("image", "image input"),
        ("model", "local model runtime"),
        ("storage", "storage"),
        ("report", "issue reporting"),
        ("layout", "chat UI"),
        ("button", "chat UI"),
    ):
        if keyword in text:
            return area
    return "general chat"


def load_reports(archive_path: Path) -> list[dict]:
    with zipfile.ZipFile(archive_path) as archive:
        reports = []
        for name in sorted(archive.namelist()):
            if not name.startswith("reports/") or not name.endswith(".json"):
                continue
            report = json.loads(archive.read(name))
            missing = REQUIRED_FIELDS - report.keys()
            if missing:
                raise ValueError(f"{name} is missing fields: {sorted(missing)}")
            report["_source"] = name
            reports.append(report)
        if not reports:
            raise ValueError("The archive contains no reports/*.json files.")
        return reports


def analyze(reports: list[dict]) -> dict:
    duplicate_counts: dict[str, int] = {}
    results = []
    for report in reports:
        key = fingerprint(report)
        duplicate_counts[key] = duplicate_counts.get(key, 0) + 1
        priority, priority_reason = classify_priority(report)
        risk, risk_reason = classify_risk(report)
        confidence = 0.9 if priority in {"P0", "P1"} else 0.7
        next_action = {
            "P0": "Review security or data-loss impact before implementation.",
            "P1": "Reproduce on the target Pixel 9a profile and capture diagnostics.",
            "P2": "Confirm the intended behavior and estimate implementation scope.",
            "P3": "Consider during the next UI or polish pass.",
        }[priority]
        results.append(
            {
                "id": report["id"],
                "source": report["_source"],
                "title": report["title"],
                "description": report.get("description", ""),
                "expected_behavior": report.get("expected_behavior", ""),
                "actual_behavior": report.get("actual_behavior", ""),
                "category": report["category"],
                "priority": priority,
                "priority_reason": priority_reason,
                "implementation_risk": risk,
                "risk_reason": risk_reason,
                "confidence": confidence,
                "affected_area": affected_area(report),
                "recommended_next_action": next_action,
                "duplicate_fingerprint": key,
            }
        )
    for result in results:
        result["duplicate_count"] = duplicate_counts[result["duplicate_fingerprint"]]
    return {
        "schema_version": 1,
        "report_count": len(results),
        "results": results,
    }


def write_markdown(path: Path, analysis: dict) -> None:
    lines = [
        "# Sancho report analysis",
        "",
        f"Reports analyzed: {analysis['report_count']}",
        "",
        "| Priority | Risk | Category | Title | Duplicates |",
        "| --- | --- | --- | --- | ---: |",
    ]
    for result in analysis["results"]:
        title = result["title"].replace("|", "\\|")
        lines.append(
            f"| {result['priority']} | {result['implementation_risk']} | "
            f"{result['category']} | {title} | {result['duplicate_count']} |"
        )
    lines.extend(["", "## Details", ""])
    for result in analysis["results"]:
        lines.extend(
            [
                f"### {result['title']}",
                "",
                f"- ID: `{result['id']}`",
                f"- Area: {result['affected_area']}",
                f"- Confidence: {result['confidence']:.1f}",
                f"- Priority rationale: {result['priority_reason']}",
                f"- Risk rationale: {result['risk_reason']}",
                f"- Recommended next action: {result['recommended_next_action']}",
                "",
                f"**Description:** {result['description']}",
            ]
        )
        if result["expected_behavior"]:
            lines.append(f"\n**Expected behavior:** {result['expected_behavior']}")
        if result["actual_behavior"]:
            lines.append(f"\n**Actual behavior:** {result['actual_behavior']}")
        lines.append("")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path, help="Path to sancho_report.zip")
    args = parser.parse_args()
    if not args.archive.is_file():
        parser.error(f"report archive does not exist: {args.archive}")
    try:
        analysis = analyze(load_reports(args.archive))
    except (OSError, ValueError, json.JSONDecodeError, zipfile.BadZipFile) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1

    json_path = args.archive.with_suffix(".analysis.json")
    markdown_path = args.archive.with_suffix(".analysis.md")
    json_path.write_text(json.dumps(analysis, indent=2) + "\n", encoding="utf-8")
    write_markdown(markdown_path, analysis)
    print(f"Wrote {json_path}")
    print(f"Wrote {markdown_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
