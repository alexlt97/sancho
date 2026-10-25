.PHONY: setup build safety instrumentation-build install launch uninstall test check dev preview preview-stop e2e benchmark gpu-benchmark device check-arch vcr verify-feature session-start session-end clean-check

setup:
	@bash scripts/bootstrap-toolchain.sh

build:
	@bash scripts/with-toolchain.sh ./gradlew assembleDebug

safety:
	@bash scripts/with-toolchain.sh bash scripts/check-apk-safety.sh

instrumentation-build:
	@bash scripts/with-toolchain.sh ./gradlew assembleDebugAndroidTest

install:
	@bash scripts/with-toolchain.sh bash scripts/ai-device.sh install

launch:
	@bash scripts/with-toolchain.sh bash scripts/ai-device.sh launch

uninstall:
	@bash scripts/with-toolchain.sh bash scripts/ai-device.sh uninstall

check:
	@bash scripts/check.sh

test:
	@bash scripts/with-toolchain.sh ./gradlew test

dev:
	@echo "No development server: this is a native Android application. Use Android Studio or an emulator/device."

preview:
	@bash scripts/with-toolchain.sh bash scripts/preview.sh start

preview-stop:
	@bash scripts/with-toolchain.sh bash scripts/preview.sh stop

e2e:
	@bash scripts/with-toolchain.sh bash scripts/ai-device.sh e2e

benchmark:
	@bash scripts/with-toolchain.sh bash scripts/ai-device.sh benchmark

gpu-benchmark:
	@bash scripts/with-toolchain.sh bash scripts/ai-device.sh gpu-benchmark

device:
	@bash scripts/with-toolchain.sh adb devices -l

check-arch:
	@bash scripts/check-arch.sh

vcr:
	@python3 scripts/vcr.py feature_list.json

verify-feature:
	@test -n "$(F)" || (echo "Usage: make verify-feature F=F000" >&2; exit 2)
	@bash scripts/verify-feature.sh "$(F)"

session-start:
	@bash scripts/session-trace.sh start

session-end:
	@bash scripts/session-trace.sh end

clean-check:
	@bash scripts/clean-state-check.sh
