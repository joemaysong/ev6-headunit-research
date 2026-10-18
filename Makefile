check:
	python3 tools/check_publication.py

# Builds require private OEM inputs and configured local toolchains.

# Pure host regressions: configure JAVA, ECJ_JAR and ANDROID_JAR first.
test-host:
	python3 carplay/tests/run_host.py
