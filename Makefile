
config ?= compileClasspath
version ?= $(shell grep 'Plugin-Version' plugins/nf-co2footprint/src/resources/META-INF/MANIFEST.MF | awk '{ print $$2 }')

ifdef module 
mm = :${module}:
else 
mm = 
endif 

NXF_HOME ?= $$HOME/.nextflow
NXF_PLUGINS_DIR ?= $(NXF_HOME)/plugins

clean:
	./gradlew clean

compile:
	./gradlew :nextflow:exportClasspath compileGroovy
	@echo "DONE `date`"


check:
	./gradlew check


#
# Show dependencies try `make deps config=runtime`, `make deps config=google`
#
deps:
	./gradlew -q ${mm}dependencies --configuration ${config}

deps-all:
	./gradlew -q dependencyInsight --configuration ${config} --dependency ${module}

#
# Refresh SNAPSHOTs dependencies
#
refresh:
	./gradlew --refresh-dependencies 

#
# Run all tests or selected ones
#
test:
ifndef class
	./gradlew ${mm}test
else
	./gradlew ${mm}test --tests ${class}
endif

install:
	./gradlew copyPluginZip
	rm -rf ${NXF_PLUGINS_DIR}/nf-co2footprint-${version}
	cp -r build/plugins/nf-co2footprint-${version} ${NXF_PLUGINS_DIR}

#
# generate build zips under build/plugins
# you can install the plugin copying manually these files to $HOME/.nextflow/plugins
#
buildPlugins:
	./gradlew copyPluginZip

#
# Upload JAR artifacts to Maven Central
#
upload:
	./gradlew upload


upload-plugins:
	./gradlew plugins:upload

publish-index:
	./gradlew plugins:publishIndex