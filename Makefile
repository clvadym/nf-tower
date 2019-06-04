config ?= compile

clean:
	./gradlew clean
	docker rm nf-tower_mongo_1 || true

test:
	./gradlew test

build:
	./gradlew assemble
	docker build -t watchtower-service:latest watchtower-service/
	docker build -t watchtower-gui:latest watchtower-gui/

run:
	docker-compose up --build

deps:
	./gradlew -q watchtower-service:dependencies --configuration ${config}

dev-up:
	./gradlew assemble
	docker-compose -f docker-livedev.yml up --build
	echo Open your browser --> http://localhost:4200/

dev-down:
	docker-compose -f docker-livedev.yml down