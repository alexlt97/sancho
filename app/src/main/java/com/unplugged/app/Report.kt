package com.unplugged.app

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

data class IssueReport(
    val id: String = UUID.randomUUID().toString(),
    val category: String = "Bug",
    val title: String,
    val description: String,
    val reproductionSteps: String = "",
    val expectedBehavior: String = "",
    val actualBehavior: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = createdAtMillis,
    val screenshotFiles: List<String> = emptyList(),
) {
    companion object {
        fun fromJson(json: JSONObject): IssueReport = IssueReport(
            id = json.getString("id"),
            category = json.optString("category", "Bug"),
            title = json.getString("title"),
            description = json.getString("description"),
            reproductionSteps = json.optString("reproduction_steps"),
            expectedBehavior = json.optString("expected_behavior"),
            actualBehavior = json.optString("actual_behavior"),
            createdAtMillis = json.optLong("created_at_millis"),
            updatedAtMillis = json.optLong("updated_at_millis"),
            screenshotFiles = buildList {
                val screenshots = json.optJSONArray("screenshots") ?: return@buildList
                for (index in 0 until screenshots.length()) add(screenshots.getString(index))
            },
        )
    }
}

class ReportStore(
    private val contentResolver: ContentResolver,
    private val reportsDirectory: File,
    private val cacheDirectory: File,
) {
    fun load(): List<IssueReport> {
        if (!reportsDirectory.exists()) return emptyList()
        return reportsDirectory.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .mapNotNull { file -> runCatching { IssueReport.fromJson(JSONObject(file.readText())) }.getOrNull() }
            .sortedByDescending { it.updatedAtMillis }
    }

    fun save(report: IssueReport) {
        reportsDirectory.mkdirs()
        val destination = File(reportsDirectory, "${report.id}.json")
        val temporary = File(reportsDirectory, ".${report.id}.json.tmp")
        temporary.writeText(report.toJson().toString())
        if (!temporary.renameTo(destination)) {
            destination.delete()
            check(temporary.renameTo(destination)) { "Could not save report ${report.id}" }
        }
    }

    fun delete(reportId: String) {
        File(reportsDirectory, "$reportId.json").delete()
        File(reportsDirectory, reportId).deleteRecursively()
    }

    fun addScreenshot(report: IssueReport, source: Uri): IssueReport {
        val attachmentDirectory = File(reportsDirectory, report.id).apply { mkdirs() }
        val screenshot = File(attachmentDirectory, "screenshot-${System.currentTimeMillis()}.jpg")
        val copiedBytes = contentResolver.openInputStream(source)?.use { input ->
            screenshot.outputStream().use { output -> input.copyTo(output) }
        } ?: 0L
        if (copiedBytes <= 0L) {
            screenshot.delete()
            error("The screenshot could not be opened.")
        }

        val updated = report.copy(
            updatedAtMillis = System.currentTimeMillis(),
            screenshotFiles = report.screenshotFiles + screenshot.name,
        )
        save(updated)
        return updated
    }

    fun export(uri: Uri): Int {
        val reports = load()
        check(reports.isNotEmpty()) { "There are no reports to export." }
        cacheDirectory.mkdirs()
        val temporary = File.createTempFile("sancho_report", ".zip", cacheDirectory)
        try {
            ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
                writeZipEntry(
                    zip,
                    "manifest.json",
                    JSONObject()
                        .put("schema_version", 1)
                        .put("report_count", reports.size)
                        .put("report_ids", JSONArray(reports.map { it.id }))
                        .toString(2)
                        .toByteArray(),
                )
                reports.forEach { report ->
                    writeZipEntry(zip, "reports/${report.id}.json", report.toJson().toString(2).toByteArray())
                    writeZipEntry(zip, "reports/${report.id}.md", report.toMarkdown().toByteArray())
                    report.screenshotFiles.forEach { name ->
                        val screenshot = File(File(reportsDirectory, report.id), name)
                        if (screenshot.isFile) {
                            zip.putNextEntry(ZipEntry("screenshots/${report.id}-$name"))
                            screenshot.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
            contentResolver.openOutputStream(uri)?.use { output ->
                temporary.inputStream().use { input -> input.copyTo(output) }
            } ?: error("The selected export destination could not be opened.")
            return reports.size
        } finally {
            temporary.delete()
        }
    }

    fun clear() {
        reportsDirectory.deleteRecursively()
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }
}

private fun IssueReport.toJson(): JSONObject = JSONObject().apply {
    put("schema_version", 1)
    put("id", id)
    put("category", category)
    put("title", title)
    put("description", description)
    put("reproduction_steps", reproductionSteps)
    put("expected_behavior", expectedBehavior)
    put("actual_behavior", actualBehavior)
    put("created_at_millis", createdAtMillis)
    put("updated_at_millis", updatedAtMillis)
    put("screenshots", JSONArray(screenshotFiles))
}

private fun IssueReport.toMarkdown(): String = buildString {
    appendLine("# $title")
    appendLine()
    appendLine("- Category: $category")
    appendLine("- Report ID: $id")
    appendLine("- Created: $createdAtMillis")
    appendLine()
    appendLine("## Description")
    appendLine(description)
    appendLine()
    appendSection("Reproduction steps", reproductionSteps)
    appendSection("Expected behavior", expectedBehavior)
    appendSection("Actual behavior", actualBehavior)
    if (screenshotFiles.isNotEmpty()) {
        appendLine("## Screenshots")
        screenshotFiles.forEach { appendLine("- $it") }
    }
}

private fun StringBuilder.appendSection(title: String, value: String) {
    if (value.isNotBlank()) {
        appendLine("## $title")
        appendLine(value)
        appendLine()
    }
}

