docker build -t verdox/open-hardware-api:2.1.5 .
docker build -t verdox/open-hardware-api:2.1.5 -t verdox/open-hardware-api:latest .

docker push verdox/open-hardware-api:2.1.5
docker push verdox/open-hardware-api:latest
