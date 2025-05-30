## Toolset
# Local: k8s, kubectl, helm, docker, maven, git, java

# Local docker repository
# docker run -d -p 5005:5000 --name registry registry:2

# Stop / uninstall helm charts
helm uninstall labs64.io
docker images --format "{{.Repository}}:{{.Tag}} {{.ID}}" | grep "module-" | awk '{print $2}' | xargs -r docker rmi -f

# Build modules
mvn clean package -f module-c/pom.xml
mvn clean package -f module-d/pom.xml

# Build docker images
docker build -t module-c-core:latest module-c
docker tag module-c-core:latest localhost:5005/module-c-core:latest
docker push localhost:5005/module-c-core:latest

docker build -t module-d-core:latest module-d
docker tag module-d-core:latest localhost:5005/module-d-core:latest
docker push localhost:5005/module-d-core:latest

docker images

# Install helm charts

## Bitnami Repo
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

## RabbitMQ
#helm uninstall rabbitmq
helm search repo bitnami/rabbitmq
#helm show values bitnami/rabbitmq > helm/rabbitmq/rabbitmq-values.orig.yaml
helm upgrade --install rabbitmq bitnami/rabbitmq -f helm/rabbitmq/values.yaml

## Kafka
#helm uninstall kafka
helm search repo bitnami/kafka
#helm show values bitnami/kafka > helm/kafka/kafka-values.orig.yaml
helm upgrade --install kafka bitnami/kafka -f helm/kafka/values.yaml

## Labs64.io
helm upgrade --install labs64.io ./helm/labs64.io

helm ls

# k8s
kubectl get pods


## Cheatsheet

# kubectl port-forward service/module-c-core 8080:80
# => http://localhost:8080/swagger-ui/index.html
# kubectl port-forward service/rabbitmq 15672:15672
# => http://localhost:15672
# kubectl port-forward service/kafka 9092:9092

# kubectl scale deployment module-d-core --replicas=0/1/2

# kubectl logs -l app=module-c-core -f
# kubectl logs -l app=module-d-core -f

# curl -X POST "http://localhost:8080/publish" -d "message=msg"
# curl -X POST "http://localhost:8080/publish" -H "Content-Type: application/json" -d '{"message":"msg"}'


## Links
# - Kubernetes – https://kubernetes.io  Open-source container orchestration platform for automating deployment, scaling, and management of containerized applications
# - Helm – https://helm.sh  Package manager for Kubernetes that uses charts to define, install, and manage applications in a Kubernetes cluster
# - Spring Cloud Stream – https://spring.io/projects/spring-cloud-stream  Framework for building event-driven microservices connected to messaging systems like Kafka or RabbitMQ using binders
# - Kafka – https://kafka.apache.org  Distributed streaming platform for building real-time data pipelines and event-driven applications with scalable publish-subscribe messaging
# - RabbitMQ – https://www.rabbitmq.com  Message broker implementing AMQP and other protocols, enabling reliable inter-service communication in distributed systems
# - OpenAPI – https://www.openapis.org  Standard for describing RESTful APIs in a machine-readable format to support documentation, validation, and code generation
# - Logfmt – https://brandur.org/logfmt  Documentation for parsing logfmt log lines into structured fields, used with Grafana Loki for log aggregation
# - FastAPI – https://fastapi.tiangolo.com  High-performance Python web framework for building APIs with automatic docs, type validation, and async support
