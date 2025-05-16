## Toolset
# local k8s, kubectl, helm, docker, maven, git, java
# VSCode plugin: redis, dapr

# docker run -d -p 5005:5000 --name registry registry:2

# Stop / uninstall helm charts
helm uninstall labs64-vanguard
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
helm upgrade --install dapr dapr/dapr --namespace dapr-system --create-namespace --set global.ha.enabled=true
kubectl get crds | grep components.dapr.io

## Redis
helm upgrade --install redis ./helm/redis -f ./helm/redis/values.yaml -f ./helm/redis/values.DEV.yaml

## Labs64 Vanguard
helm upgrade --install labs64-vanguard ./helm/labs64-vanguard

helm ls

# k8s
kubectl get pods

## Cheatsheet

# kubectl port-forward service/module-a-core 8080:80
# kubectl port-forward service/redis 6379:6379

# curl -X POST "http://localhost:8080/publish" -d "message=msg"
# kubectl scale deployment module-b-core --replicas=0/1/2

# kc logs -l app=module-b-core -f
#for pod in $(kubectl get pods -l app=module-b-core -o jsonpath='{.items[*].metadata.name}'); do
#  kubectl logs -f "$pod" | sed "s/^/[$pod] /" &
#done
#wait
