#!/bin/bash

# Load environment variables from parent .env
if [ -f ../.env ]; then
  echo "Loading environment variables from ../.env"
  export $(cat ../.env | grep -v '^#' | xargs)
fi

# Run Spring Boot
./mvnw spring-boot:run
