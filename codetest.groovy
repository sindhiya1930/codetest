def getEnvVar(String paramName){
    //get the env from properties file
	return sh (script:"grep '${paramName}' /opt/properties/phase1b_properties/dev_properties/${SERVICE_NAME}.properties|cut -d'=' -f2", returnStdout: true).trim();
}


pipeline{
    agent any
           environment {
               SERVICE_NAME = sh(script: "git describe --tags \$(git rev-list --tags --max-count=1)| cut -d'_' -f1", , returnStdout: true).trim()
          }
   
	stages {
		
				stage('Sence'){
			steps{
			sh '''

			echo ${SERVICE_NAME}

			'''
			}
			}
	stage('Initialization'){
            steps{
                //checkout scm
                script{
		env.BASE_DIR = pwd()
                env.IMAGE_NAME = getEnvVar('IMAGE_NAME')
		env.CATEGORY = getEnvVar('CATEGORY')
                env.JENKINS_GCLOUD_PROJECT_ID = getEnvVar('JENKINS_GCLOUD_PROJECT_ID')
                env.JENKINS_GCLOUD_K8S_CLUSTER_ZONE = getEnvVar('JENKINS_GCLOUD_K8S_CLUSTER_ZONE')
                env.JENKINS_GCLOUD_K8S_CLUSTER_REGION = getEnvVar('JENKINS_GCLOUD_K8S_CLUSTER_REGION')
                env.DEPLOY_GCLOUD_PROJECT_ID_DEV= getEnvVar('DEPLOY_GCLOUD_PROJECT_ID_DEV')
                env.DEPLOY_GCLOUD_K8S_CLUSTER_NAME_DEV = getEnvVar('DEPLOY_GCLOUD_K8S_CLUSTER_NAME_DEV')
                env.DEPLOY_GCLOUD_K8S_CLUSTER_ZONE_DEV = getEnvVar('DEPLOY_GCLOUD_K8S_CLUSTER_ZONE_DEV')
                env.DEPLOY_GCLOUD_K8S_CLUSTER_REGION_DEV = getEnvVar('DEPLOY_GCLOUD_K8S_CLUSTER_REGION_DEV')
                env.DEPLOYMENT_NAME = getEnvVar('DEPLOYMENT_NAME')
		env.PARAMETERS = getEnvVar('PARAMETERS')
		env.URL = getEnvVar('URL')
                env.PATH_TO_PARENT_POM = getEnvVar('PATH_TO_PARENT_POM')
		env.B_ProjectName = getEnvVar('B_ProjectName')	
                }
            }
        }
		
 stage('Git Checkout') { // for display purposes 
            steps{
                cleanWs()
		checkout([$class: 'GitSCM', branches: [[name: '*/dev']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'git-service-acc', url: 'https://github.com/mattel-dig/ConsumerMaster--GSL-.git']]])
	    }
	}
   
        stage('PreBuild'){
            steps{
                //Builds the container from Dockerfile
                sh '''
                #description : The script is used to fetch the dependent shared module with respect to the API.
                #!/bin/bash
		echo ${SERVICE_NAME}
		 mkdir /var/lib/jenkins/workspace/${JOB_NAME}/$SERVICE_NAME
		 chmod -R 777 /var/lib/jenkins/workspace/${JOB_NAME}/$SERVICE_NAME
                #Transfer of API and API files to the workspace
                cp -r /var/lib/jenkins/workspace/${JOB_NAME}/code_rearch/${CATEGORY}/$SERVICE_NAME/* /var/lib/jenkins/workspace/${JOB_NAME}/$SERVICE_NAME/
                #Get the list of shared modules currently present
                cd /var/lib/jenkins/workspace/${JOB_NAME}/$SERVICE_NAME
                ls | grep 'Mattel.*.parent' > ms_parent.txt
                B="`cat ms_parent.txt`"
                cd /var/lib/jenkins/workspace/${JOB_NAME}/code_rearch/SharedModules/
                ls > SM_list.txt
                A="`cat SM_list.txt`"
                shared_module="`echo $A`"
                for i in $shared_module
                    do
                        module=`grep $i /var/lib/jenkins/workspace/${JOB_NAME}/code_rearch/${CATEGORY}/${SERVICE_NAME}/ReadMe.txt | wc -l`
                        if [ $module -eq 1 ]; then
                            echo "Shared module is present in the ReadMe.txt and has to be copied to the workspace"
                            cp -r $i /var/lib/jenkins/workspace/${JOB_NAME}/$SERVICE_NAME/
                            sed -i "s/Mattel.*.parent/`echo $B`/g" /var/lib/jenkins/workspace/${JOB_NAME}/${SERVICE_NAME}/$i/pom.xml
                        else
                            echo "Shared Module not present in the ReadMe.txt"
                        fi
                    done
                > SM_list.txt
		cd /var/lib/jenkins/workspace/${JOB_NAME}/$SERVICE_NAME
                	ls | grep 'Mattel.*.application' > ms_application.txt
                	App_folder="`head -1 ms_application.txt`"
                ''' 
            }   
        }
 
		 stage('Automated Code Review'){
            steps {
                step([$class: 'TibcoBartPipeline', 
    	              bartHome:'/opt/Bart_home',
    	              bartVer:'1.0',
    	              projectName:"${B_ProjectName}",
    	              projectWorkSpace:"/var/lib/jenkins/workspace/${JOB_NAME}/${SERVICE_NAME}",
    	              reportDir:"${workspace}"])
		  }
        	}

   
         stage('Build') {
            steps {
                //build using pom.xml - specify the path of the parent pom
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'subram',usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                sh '''
                mvn -f ${PATH_TO_PARENT_POM}/pom.xml clean install
		
		
		'''
                }
            }
        }
		
		stage('Docker Containerisation'){
            steps{
                //Builds the container from Dockerfile
                sh '''
                GIT_COMMIT_HASH=`git log -n 1 --pretty=format:%H`
                echo $GIT_COMMIT_HASH
                cd /var/lib/jenkins/workspace/${JOB_NAME}/${SERVICE_NAME}/${B_ProjectName}/target/
                cp /var/lib/jenkins/workspace/${JOB_NAME}/deploy_rearch/dockerfiles/${CATEGORY}/${SERVICE_NAME}/Dockerfile Dockerfile
                docker build -t gcr.io/${JENKINS_GCLOUD_PROJECT_ID}/${IMAGE_NAME}:$GIT_COMMIT_HASH .
                ''' 
            }   
        }
          
        stage('Image Publish to GCR'){
            steps{
                sh '''
                #This gets the Git commit id 
                GIT_COMMIT_HASH=`git log -n 1 --pretty=format:%H`
                echo $GIT_COMMIT_HASH
                gcloud config set compute/zone ${JENKINS_GCLOUD_K8S_CLUSTER_ZONE}       
                gcloud config set compute/region ${JENKINS_GCLOUD_K8S_CLUSTER_REGION}
                gcloud config set project ${JENKINS_GCLOUD_PROJECT_ID}
                gcloud auth configure-docker
                #Pushes Docker images into GCR
                docker push gcr.io/${JENKINS_GCLOUD_PROJECT_ID}/${IMAGE_NAME}:$GIT_COMMIT_HASH
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
                    GIT_COMMIT_HASH=`git log -n 1 --pretty=format:%H`
		    echo $GIT_COMMIT_HASH
                    sed -i s/latest/`echo $GIT_COMMIT_HASH`/g /opt/deploymentfiles/phase1b_deployment/dev_deployment/${SERVICE_NAME}_dev.yml
                    #Checks for any deployment
                    #kubectl get deployments ${DEPLOYMENT_NAME}
                    #RESULT=$?
                    #echo $RESULT
                    #if [ $RESULT -eq 1 ]; then
                    echo "Deployment already exists! so updating the deployment"
                    kubectl apply -f /opt/deploymentfiles/phase1b_deployment/dev_deployment/${SERVICE_NAME}_dev.yml
                    kubectl rollout status deployment ${DEPLOYMENT_NAME}
                    #else
                    #echo "Creating a new deployment"
                    #kubectl create -f /opt/deploymentfiles/phase1b_deployment/dev_deployment/${SERVICE_NAME}_dev.yml
                    #fi
                    ''' 
                }
            }
        } 
}
}
		
