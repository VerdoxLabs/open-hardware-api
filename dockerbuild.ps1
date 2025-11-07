docker build -t verdox/open-hardware-api:1.9.4.8 .
docker build -t verdox/open-hardware-api:1.9.4.8 -t verdox/open-hardware-api:latest .

docker push verdox/open-hardware-api:1.9.4.8
docker push verdox/open-hardware-api:latest
