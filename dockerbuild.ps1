docker build -t verdox/open-hardware-api:2.1.18 .
docker build -t verdox/open-hardware-api:2.1.18 -t verdox/open-hardware-api:latest .

docker push verdox/open-hardware-api:2.1.18
docker push verdox/open-hardware-api:latest
