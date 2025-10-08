docker build -t verdox/open-hardware-api:1.0.2 .
docker build -t verdox/open-hardware-api:1.0.2 -t verdox/open-hardware-api:latest .

docker push verdox/open-hardware-api:1.0.2
docker push verdox/open-hardware-api:latest
