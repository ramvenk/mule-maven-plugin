muleHome1 = "${basedir}/target/mule0/mule-enterprise-standalone-${muleVersion}"
fileContents1 = new File(muleHome1 + "/logs/mule_ee.log").text
muleExecutable1 = muleHome1 + "/bin/mule"

muleHome2 = "${basedir}/target/mule1/mule-enterprise-standalone-${muleVersion}"
fileContents2 = new File(muleHome2 + "/logs/mule_ee.log").text
muleExecutable2 = muleHome2 + "/bin/mule"

process1 = (muleExecutable1 + " stop").execute()
process1.waitFor()
assert 0 == process1.exitValue()

process2 = (muleExecutable2 + " stop").execute()
process2.waitFor()
assert 0 == process2.exitValue()

fileContents1.contains("Hazelcast Community Edition 3.1.6")
fileContents2.contains("Hazelcast Community Edition 3.1.6")
assert new File(muleHome1 + "/.mule/mule-cluster.properties").exists()
assert new File(muleHome2 + "/.mule/mule-cluster.properties").exists()
appWasDeployed1 = new File(muleHome1 + "/apps/deploy-cluster-anchor.txt").exists()
appWasDeployed2 = new File(muleHome2 + "/apps/deploy-cluster-anchor.txt").exists()
jarInstalled1 = new File(muleHome1 + "lib/user/activemq-all-5.5.0.jar")
jarInstalled2 = new File(muleHome2 + "lib/user/activemq-all-5.5.0.jar")
assert appWasDeployed1
assert appWasDeployed2
assert jarInstalled1
assert jarInstalled2