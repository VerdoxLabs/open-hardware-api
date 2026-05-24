docker build -t verdox/open-hardware-api:2.2.46 .
docker build -t verdox/open-hardware-api:2.2.46 -t verdox/open-hardware-api:latest .

docker push verdox/open-hardware-api:2.2.46
docker push verdox/open-hardware-api:latest