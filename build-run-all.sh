## Toolset
# Local: k8s, kubectl, helm, docker, maven, git, java
# IDE plugins: redis

# Local docker repository
# docker run -d -p 5005:5000 --name registry registry:2

# Stop / uninstall helm charts
helm uninstall labs64.io
docker images --format "{{.Repository}}:{{.Tag}} {{.ID}}" | grep "module-" | awk '{print $2}' | xargs -r docker rmi -f

# Build modules
mvn clean package -f module-a/pom.xml
mvn clean package -f module-b/pom.xml
mvn clean package -f module-c/pom.xml
mvn clean package -f module-d/pom.xml

# Build docker images
docker build -t module-a-core:latest module-a
docker tag module-a-core:latest localhost:5005/module-a-core:latest
docker push localhost:5005/module-a-core:latest

docker build -t module-b-core:latest module-b
docker tag module-b-core:latest localhost:5005/module-b-core:latest
docker push localhost:5005/module-b-core:latest

docker build -t module-c-core:latest module-c
docker tag module-c-core:latest localhost:5005/module-c-core:latest
docker push localhost:5005/module-c-core:latest

docker build -t module-d-core:latest module-d
docker tag module-d-core:latest localhost:5005/module-d-core:latest
docker push localhost:5005/module-d-core:latest

docker images

# Install helm charts
## DAPR
helm repo add dapr https://dapr.github.io/helm-charts/
helm repo update
helm search repo dapr
helm upgrade --install dapr dapr/dapr --namespace dapr-system --create-namespace --set global.ha.enabled=true
kubectl get crds | grep components.dapr.io

## Bitnami Repo
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

## Redis
#helm uninstall redis
helm search repo bitnami/redis
#helm show values bitnami/redis > helm/redis/redis-values.orig.yaml
helm upgrade --install redis bitnami/redis -f helm/redis/values.yaml

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

# kubectl port-forward service/module-a-core 8080:80
# kubectl port-forward service/module-c-core 8080:80
# kubectl port-forward service/rabbitmq 15672:15672
# kubectl port-forward service/kafka 9092:9092
# kubectl port-forward service/redis-master 6379:6379

# kubectl scale deployment module-d-core --replicas=0/1/2

# kubectl logs -l app=module-a-core -f
# kubectl logs -l app=module-b-core -f
# kubectl logs -l app=module-c-core -f
# kubectl logs -l app=module-d-core -f

# curl -X POST "http://localhost:8080/publish" -d "message=msg"
# curl -X POST "http://localhost:8080/publish" -H "Content-Type: application/json" -d '{"message":"msg"}'
