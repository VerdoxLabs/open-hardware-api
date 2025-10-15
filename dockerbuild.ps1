docker build -t verdox/open-hardware-api:1.6.2 .
docker build -t verdox/open-hardware-api:1.6.2 -t verdox/open-hardware-api:latest .

docker push verdox/open-hardware-api:1.6.2
docker push verdox/open-hardware-api:latest
