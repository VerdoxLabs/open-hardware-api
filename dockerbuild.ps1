docker build -t verdox/open-hardware-api:1.0.1 .
docker build -t verdox/open-hardware-api:1.0.1 -t verdox/open-hardware-api:latest .

docker push verdox/open-hardware-api:1.0.1
docker push verdox/open-hardware-api:latest
