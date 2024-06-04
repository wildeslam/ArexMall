docker stop mall-portal-origin
docker container rm mall-portal-origin

cd ..
mvn clean package -DskipTests

cd dockerfiles
cp ../mall-portal/target/mall-portal-1.0-SNAPSHOT.jar mall-portal-1.0-SNAPSHOT.jar

cp ../../../arex-agent-java/arex-agent-jar/arex-agent.jar arex-agent.jar
cp ../../../arex-agent-java/arex-agent-jar/arex-agent-bootstrap.jar arex-agent-bootstrap.jar

# docker build -f MallSearch.Dockerfile -t mall-search .
# docker build -f MallAdmin.Dockerfile -t mall-admin .
docker build -f MallPortal.Dockerfile -t mall-portal-origin .

# rm mall-search-1.0-SNAPSHOT.jar
# rm mall-admin-1.0-SNAPSHOT.jar
rm mall-portal-1.0-SNAPSHOT.jar
rm arex-agent.jar
rm arex-agent-bootstrap.jar



docker run -p 8084:8084 --name mall-portal-origin --net="arex-mall_default" \
--link mysql:db \
--link redis:redis \
--link mongo:mongo \
--link rabbitmq:rabbit \
-v /etc/localtime:/etc/localtime \
-v /mydata/app/portal-origin/logs:/var/logs \
-d mall-portal-origin
