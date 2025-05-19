## Toolset
# Local: k8s, kubectl, helm, docker, maven, git, java
# IDE plugins: redis

# Local docker repository
# docker run -d -p 5005:5000 --name registry registry:2

# Stop / uninstall helm charts
helm uninstall labs64.io
docker images --format "{{.Repository}}:{{.Tag}} {{.ID}}" | grep "module-" | awk '{print $2}' | xargs -r docker rmi -f

# Build modules
mvn clean package -f module-a/core/pom.xml
mvn clean package -f module-b/core/pom.xml

# Build docker images
docker build -t module-a-core:latest module-a/core
docker tag module-a-core:latest localhost:5005/module-a-core:latest
docker push localhost:5005/module-a-core:latest

docker build -t module-b-core:latest module-b/core
docker tag module-b-core:latest localhost:5005/module-b-core:latest
docker push localhost:5005/module-b-core:latest

docker images

# Install helm charts
## DAPR
helm repo add dapr https://dapr.github.io/helm-charts/
helm repo update
helm search repo dapr
helm upgrade --install dapr dapr/dapr --namespace dapr-system --create-namespace --set global.ha.enabled=true
kubectl get crds | grep components.dapr.io

## Redis
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
helm search repo bitnami/redis
#helm show values bitnami/redis > helm/redis/redis-values.orig.yaml
helm upgrade --install redis bitnami/redis -f helm/redis/values.yaml

## Labs64.io
helm upgrade --install labs64.io ./helm/labs64.io

helm ls

# k8s
kubectl get pods


## Cheatsheet

# kubectl port-forward service/module-a-core 8080:80
# kubectl port-forward service/redis-master 6379:6379

# kubectl scale deployment module-b-core --replicas=0/1/2

# kubectl logs -l app=module-a-core -f
# kubectl logs -l app=module-b-core -f

# curl -X POST "http://localhost:8080/publish" -d "message=msg"
