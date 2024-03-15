#!groovy

pipeline {
    environment {
        JAVA_HOME = "/usr/lib/jvm/java-17-openjdk-amd64"
        CI = "false"
        MY_VERSION = sh(
                script: 'if [[ $BRANCH_NAME =~ ^[0-9]+.[0-9]+.[0-9]+-alkimi$ ]]; then echo "${BRANCH_NAME}"; else echo "0.0.${BUILD_ID}-${BRANCH_NAME}-SNAPSHOT"; fi',
                returnStdout: true
        ).trim()
        MY_ENV = sh(
                script: 'if [[ $BRANCH_NAME =~ ^[0-9]+.[0-9]+.[0-9]+-alkimi$ ]]; then echo "prod"; elif [[ $BRANCH_NAME =~ ^[0-9]+.[0-9]+.[0-9]+-alkimi$ ]]; then echo qa; else echo "dev"; fi',
                returnStdout: true
        ).trim()
    }
    options {
        disableConcurrentBuilds()
    }
    agent any
    stages {
        stage('Prepare build') {
           steps {
               script {
                  sh 'cp ./src/main/resources/bidder-config/alkimi.yaml.${MY_ENV} ./src/main/resources/bidder-config/alkimi.yaml'
               }
           }
        }
	    stage('Build') {
            steps {
                script {
                    sh "echo ${BRANCH_NAME} ${GIT_BRANCH} ${GIT_COMMIT} ${MY_VERSION} ${MY_ENV}"
 			        sh "mvn clean package -Dmaven.test.skip=true -Drevision=${MY_VERSION}"
//			        sh "mvn clean package -Drevision=${MY_VERSION}"
                }
            }
        }
        stage('Deploy to dev') {
            when {
                branch "master_asterio"
            }
            steps {
                dir('ansible') {
                    git branch: 'migration_to_gcp', url: "git@github.com:Alkimi-Exchange/alkimi-ansible.git", credentialsId: 'ssh-alkimi-ansible'
                    withCredentials([file(credentialsId: 'exchange_service_account_file', variable: 'sa_file')]) {
                        sh "cp -f ${sa_file} ./service_account_gcp.json"
                    }
                    sh "ansible-playbook ./apps/dev/prebid-server.yml --extra-vars='artifactPath=${env.WORKSPACE}/target/prebid-server.jar configPath=${env.WORKSPACE}/config'"
                }
            }
        }
        stage('Build and push docker images') {
            steps {
                script {
                    if (env.BRANCH_NAME =~ "\\d+\\.\\d+\\.\\d+-alkimi") {
                        sh "gcloud -q auth configure-docker europe-west2-docker.pkg.dev"
                        docker.withRegistry('https://europe-west2-docker.pkg.dev') {
                            def dockerImage = docker.build("europe-west2-docker.pkg.dev/alkimi-exchange-dev/alkimi-exchange/prebid-server:${MY_VERSION}", "--build-arg APP_NAME=prebid-server -f docker/Dockerfile ${WORKSPACE}")
                            dockerImage.push()
                            dockerImage.push('latest')
                        }
                    }
                }
            }
        }
    }
}
