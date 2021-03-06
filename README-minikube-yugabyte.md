
# Minikube Installation and Setup with Yugabyte

Follow these instructions for installing and running the woe-twin microservice using Minikube and Yugabyte.

### Prerequisites

Clone the weo-twin Github project.

~~~bash
$ git clone https://github.com/mckeeh3/woe-twin.git
~~~

### Install Minikube and Kubernetes CLI

Follow the [instructions](https://kubernetes.io/docs/tasks/tools/install-minikube/) for installing Minikube and the Kubernetes CLI `kubectl`.

The `kubectl` CLI provides a nice Kubectl Autocomplete feature for `bash` and `zsh`.
See the [kubectl Cheat Sheet](https://kubernetes.io/docs/reference/kubectl/cheatsheet/#kubectl-autocomplete) for instructions.

Also, consider installing [kubectx](https://github.com/ahmetb/kubectx), which also includes `kubens`.
Mac:
~~~bash
$ brew install kubectx
~~~
Arch Linux:
~~~bash
$ yay kubectx
~~~

### Install Yugabyte for use with MiniKube

Follow the [Yugabyte Quick Start](https://docs.yugabyte.com/latest/quick-start/) guide for instrucation on installing on your local system.

### Create Cassandra Tables - Yugabyte Cassandra

Cd into the directory where you cloned the `woe-twin` repo.

~~~bash
$ <path-to-yugabyte>/bin/ycqlsh
~~~

~~~
Connected to local cluster at localhost:9042.
[ycqlsh 5.0.1 | Cassandra 3.9-SNAPSHOT | CQL spec 3.4.2 | Native protocol v4]
Use HELP for help.
ycqlsh> 
~~~

Run script to create the required Akka persistence tables.

~~~
ycqlsh> source 'src/main/resources/akka-persistence-journal-create-twin.cql'
~~~

Verify that the tables have been created.

~~~
ycqlsh> use woe_twin;
ycqlsh:woe_twin> describe tables;

tag_views  tag_scanning         tag_write_progress
messages   all_persistence_ids  metadata          

ycqlsh:woe_twin> quit
~~~

### Create PostgreSQL Table - Yugabyte PostgreSQL

~~~bash
$ <path-to-yugabyte-install-dir>/bin/ysqlsh
~~~
~~~
ysqlsh (11.2-YB-2.3.1.0-b0)
Type "help" for help.

yugabyte=# 
~~~

Execute the create table DDL script to create the projection table.
~~~
yugabyte=# \i src/main/resources/region-projection.sql
CREATE TABLE
yugabyte=# 
~~~

Execute the create table DDL script tp create the Akka Projection offset table.
~~~
yugabyte=# \i src/main/resources/akka-projection-offset-store.sql 
CREATE TABLE
CREATE INDEX
yugabyte=# 
~~~

Verify that the tables have been created.

~~~
yugabyte=# \d
                    List of relations
 Schema |             Name             | Type  |  Owner   
--------+------------------------------+-------+----------
 public | AKKA_PROJECTION_OFFSET_STORE | table | yugabyte
 public | region                       | table | yugabyte
(2 rows)

yugabyte=# quit
~~~

## Start Minikube

You may want to allocate more CPU and memory capacity to run the WoW application than the defaults. There are two `minikube` command options available for adjusting the CPU and memory allocation settings.

~~~bash
$ minikube start --cpus=C --memory=M
~~~

For example, allocate 4 CPUs and 10 gig of memory.

~~~bash
$ minikube start --cpus=4 --memory=10g
~~~

### Build and Deploy to MiniKube

From the woe-twin project directory.

Before the build, set up the Docker environment variables using the following commands.
~~~bash
$ minikube docker-env
~~~
~~~
export DOCKER_TLS_VERIFY="1"
export DOCKER_HOST="tcp://192.168.99.102:2376"
export DOCKER_CERT_PATH="/home/hxmc/.minikube/certs"
export MINIKUBE_ACTIVE_DOCKERD="minikube"

# To point your shell to minikube's docker-daemon, run:
# eval $(minikube -p minikube docker-env)
~~~
Copy and paster the above `eval` command.
~~~bash
$ eval $(minikube -p minikube docker-env)
~~~

Build the project, which will create a new Docker image.
~~~bash
$ mvn clean package docker:build
~~~
~~~
...
[INFO] 
[INFO] --- docker-maven-plugin:0.26.1:build (default-cli) @ woe-twin ---
[INFO] Copying files to /home/hxmc/Lightbend/akka-java/woe-twin/target/docker/woe-twin/build/maven
[INFO] Building tar: /home/hxmc/Lightbend/akka-java/woe-twin/target/docker/woe-twin/tmp/docker-build.tar
[INFO] DOCKER> [woe-twin:latest]: Created docker-build.tar in 973 milliseconds
[INFO] DOCKER> [woe-twin:latest]: Built image sha256:991bc
[INFO] DOCKER> [woe-twin:latest]: Tag with latest,20200924-082133.8137be6
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  01:16 min
[INFO] Finished at: 2020-09-24T08:37:04-04:00
[INFO] ------------------------------------------------------------------------
~~~

Create the Kubernetes namespace. The namespace only needs to be created once.
~~~bash
$ kubectl create namespace woe-twin-1     
~~~
~~~
namespace/woe-twin-1 created
~~~

Set this namespace as the default for subsequent `kubectl` commands.
~~~bash
$ kubectl config set-context --current --namespace=woe-twin-1
~~~
~~~
Context "minikube" modified.
~~~

Deploy the Docker images to the Kubernetes cluster.
~~~bash
$ kubectl apply -f kubernetes/akka-cluster.yml
~~~
~~~
deployment.apps/woe-twin created
role.rbac.authorization.k8s.io/pod-reader created
rolebinding.rbac.authorization.k8s.io/read-pods created
~~~
Check if the pods are running. This may take a few moments.
~~~bash
$ kubectl get pods                                          
~~~
~~~
NAME                      READY   STATUS    RESTARTS   AGE
woe-twin-786f7bb8dc-6cpp7   1/1     Running   0          3m7s
woe-twin-786f7bb8dc-98v26   1/1     Running   0          3m7s
woe-twin-786f7bb8dc-hmxlx   1/1     Running   0          3m7s
~~~

If there are configuration issues or if you want to check something in a container, start a `bash` shell in one of the pods using the following command. For example, start a `bash` shell on the 3rd pod listed above.

~~~bash
$ kubectl exec -it woe-twin-786f7bb8dc-hmxlx -- /bin/bash                        
root@woe-twin-786f7bb8dc-hmxlx:/# 
~~~
~~~
root@woe-twin-786f7bb8dc-hmxlx:/# env | grep woe
HOSTNAME=woe-twin-786f7bb8dc-hmxlx
woe_twin_http_server_port=8080
NAMESPACE=woe-twin-1
woe_simulator_http_server_port=8080
woe_twin_grpc_server_port=8081
woe_simulator_http_server_host=woe-sim-service.woe-sim-1.svc.cluster.local
woe_twin_http_server_host=woe-twin-service.woe-twin-1.svc.cluster.local
woe_twin_grpc_server_host=woe-twin-service.woe-twin-1.svc.cluster.local
root@woe-twin-786f7bb8dc-hmxlx:/# exit
exit
command terminated with exit code 127
~~~

#### Enable External Access

Create a load balancer to enable access to the WOE Twin microservice HTTP endpoint.

~~~bash
$ kubectl expose deployment woe-twin --type=LoadBalancer --name=woe-twin-service
~~~
~~~
service/woe-twin-service exposed
~~~

Next, view to external port assignments.

~~~bash
$ kubectl get services woe-twin-service
~~~
~~~
NAME               TYPE           CLUSTER-IP      EXTERNAL-IP   PORT(S)                                        AGE
woe-twin-service   LoadBalancer   10.106.25.172   <pending>     2552:31029/TCP,8558:32559/TCP,8080:32171/TCP   5h4m
~~~

Note that in this example, the Kubernetes internal port 8080 external port assignment of 32171.

For MiniKube deployments, the full URL to access the HTTP endpoint is constructed using the MiniKube IP and the external port.

~~~bash
$ minikube ip       
~~~
In this example the MiniKube IP is:
~~~
192.168.99.102
~~~
Try accessing this endpoint using the curl command or from a browser. Use the external port defined for port 8080. In the example above the external port for port 8080 is 32171.

~~~bash
$ curl -v http://$(minikube ip):32171
~~~
~~~
*   Trying 192.168.99.102:32171...
* Connected to 192.168.99.102 (192.168.99.102) port 32171 (#0)
> GET / HTTP/1.1
> Host: 192.168.99.102:32171
> User-Agent: curl/7.70.0
> Accept: */*
>
* Mark bundle as not supporting multiuse
< HTTP/1.1 200 OK
< Last-Modified: Thu, 18 Jun 2020 14:31:18 GMT
< ETag: "52800172c7d756f0"
< Accept-Ranges: bytes
< Server: akka-http/10.1.12
< Date: Fri, 19 Jun 2020 00:26:36 GMT
< Content-Type: text/html; charset=UTF-8
< Content-Length: 330
<
<!DOCTYPE html>
<html lang="en">

<head>
  <title>Of Things Internet</title>
  <script src="p5.js" type="text/javascript"></script>
  <script src="mappa.js" type="text/javascript"></script>
  <script src="woe.js" type="text/javascript"></script>
  <style> body { padding: 0; margin: 0; }</style>
</head>

<body>
</body>

</html>

* Connection #0 to host 192.168.99.102 left intact
~~~
