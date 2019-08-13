def getEnvVar(String paramName){
    //get the env from properties file
	return sh (script:"grep '${paramName}' /var/lib/jenkins/workspace/${JOB_NAME}/properties/dev.properties|cut -d'=' -f2", returnStdout: true).trim();
}
pipeline{
    agent any
           environment {
               SERVICE_NAME = sh(script: "git describe --tags \$(git rev-list --tags --max-count=1)| cut -d'_' -f1", ,returnStdout: true).trim()
          }

	stages {
   		stage('Git Checkout') { // for display purposes 
            steps{
                cleanWs()
		//checkout([$class: 'GitSCM', branches: [[name: 'refs/tags/*'], [name: '*/dev']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '4f18f877-3658-4932-98f0-eb4d12fe1d82', refspec: '+refs/tags/*:refs/remotes/origin/tags/*', url: 'https://github.com/sindhiya1930/codetest.git']]])
		   checkout([$class: 'GitSCM', branches: [[name: 'refs/tags/*_dev_*']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '4f18f877-3658-4932-98f0-eb4d12fe1d82', refspec: '+refs/tags/*:refs/remotes/origin/tags/*', url: 'https://github.com/sindhiya1930/codetest.git']]])
		    //checkout poll: false, scm: [$class: 'GitSCM', branches: [[name: 'refs/tags/*']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '4f18f877-3658-4932-98f0-eb4d12fe1d82', refspec: '+refs/tags/*:refs/remotes/origin/tags/*', url: 'https://github.com/sindhiya1930/codetest.git']]]
			}
		}

		
		stage('Initialization'){
            steps{
                //checkout scm
		
                script{	
		def CATEGORY_CHECK= sh(script: "echo $SERVICE_NAME|cut -d'-' -f2", ,returnStdout: true).trim()

		if (CATEGORY_CHECK=='ms') {
                env.CATEGORY= sh(script: "echo 'Microservice'", ,returnStdout: true).trim()

                }
		else {
		env.CATEGORY= sh(script: "echo 'API'", ,returnStdout: true).trim()
		}
		
		env.CODE_FOLDER_NAME = getEnvVar('CODE_FOLDER_NAME')
		env.DEPLOY_FOLDER_NAME = getEnvVar('DEPLOY_FOLDER_NAME')
                env.JENKINS_GCLOUD_PROJECT_ID = getEnvVar('JENKINS_GCLOUD_PROJECT_ID')
                env.JENKINS_GCLOUD_K8S_CLUSTER_ZONE = getEnvVar('JENKINS_GCLOUD_K8S_CLUSTER_ZONE')
                env.JENKINS_GCLOUD_K8S_CLUSTER_REGION = getEnvVar('JENKINS_GCLOUD_K8S_CLUSTER_REGION')
                env.DEPLOY_GCLOUD_PROJECT_ID_DEV= getEnvVar('DEPLOY_GCLOUD_PROJECT_ID_DEV')
                env.DEPLOY_GCLOUD_K8S_CLUSTER_NAME_DEV = getEnvVar('DEPLOY_GCLOUD_K8S_CLUSTER_NAME_DEV')
                env.DEPLOY_GCLOUD_K8S_CLUSTER_ZONE_DEV = getEnvVar('DEPLOY_GCLOUD_K8S_CLUSTER_ZONE_DEV')
                env.DEPLOY_GCLOUD_K8S_CLUSTER_REGION_DEV = getEnvVar('DEPLOY_GCLOUD_K8S_CLUSTER_REGION_DEV')
		env.NAME = getEnvVar(env.SERVICE_NAME)
		env.PROJECT_NAME=sh(script: "echo $NAME|cut -d' ' -f1", ,returnStdout: true).trim()
		
                }
            }
        }

   	stage('Initialization1'){
            steps{
                //checkout scm
                sh '''
		echo $SERVICE_NAME
		echo $PROJECT_NAME
		echo $CATEGORY
		echo $CATEGORY_CHECK
		'''
            }
        }
		
	stage('Deployment to DEV GKE'){
            steps{
                withCredentials([file(credentialsId: 'mattelCreds', variable: 'mattel')]) {
                    sh '''
                    #Sets the env for gcloud
                    gcloud auth activate-service-account --key-file=${mattel}
                    gcloud config set compute/zone ${DEPLOY_GCLOUD_K8S_CLUSTER_ZONE_DEV}
                    gcloud config set compute/region ${DEPLOY_GCLOUD_K8S_CLUSTER_REGION_DEV}
                    gcloud config set project ${DEPLOY_GCLOUD_PROJECT_ID_DEV}
                    #Though --zone is mentioned for get-credentials ,provide the region
                    gcloud container clusters get-credentials ${DEPLOY_GCLOUD_K8S_CLUSTER_NAME_DEV} --zone ${DEPLOY_GCLOUD_K8S_CLUSTER_REGION_DEV} 
	       #Checks for any deployment
               #kubectl get deployments cm-${SERVICE_NAME}
	       
	       DeployState=`kubectl get deployments|grep "cm-${SERVICE_NAME} " | wc -l`
	       if [ $DeployState -eq 1 ]; then
                    echo "Deployment already exists! so updating the deployment"
		   else
		   echo "Creating a new deployment"
		   fi
		  }
		  }
		}
	}
}
