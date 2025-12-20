docker build -t verdox/open-hardware-api:2.2.25 .
docker build -t verdox/open-hardware-api:2.2.25 -t verdox/open-hardware-api:latest .

docker push verdox/open-hardware-api:2.2.25
docker push verdox/open-hardware-api:latest
same