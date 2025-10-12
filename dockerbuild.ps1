docker build -t verdox/open-hardware-api:1.3.6 .
docker build -t verdox/open-hardware-api:1.3.6 -t verdox/open-hardware-api:latest .

docker push verdox/open-hardware-api:1.3.6
docker push verdox/open-hardware-api:latest
