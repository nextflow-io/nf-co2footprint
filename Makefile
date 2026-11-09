# Build the plugin
assemble:
	./gradlew assemble

clean:
	rm -rf .nextflow*
	rm -rf work
	rm -rf build
	rm -rf docs/.venv
	./gradlew clean

# Run plugin unit tests
test:
	./gradlew test

# Install the plugin into local nextflow plugins dir
install:
	./gradlew install

# Publish the plugin
release:
	./gradlew releasePlugin

# Preview the docs (requires uv)
docs-serve:
	uv run --project docs mkdocs serve

# Build the docs the way CI does
docs-build:
	uv run --project docs --locked mkdocs build --strict
