package com.github.howaric.docker_rapido.core;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.howaric.docker_rapido.docker.DockerProxy;
import com.github.howaric.docker_rapido.docker.DockerProxyFactory;
import com.github.howaric.docker_rapido.exceptions.IllegalImageTagsException;
import com.github.howaric.docker_rapido.utils.RapidoLogCentre;
import com.github.howaric.docker_rapido.yaml_model.Deploy;
import com.github.howaric.docker_rapido.yaml_model.Node;
import com.github.howaric.docker_rapido.yaml_model.RapidoTemplate;
import com.github.howaric.docker_rapido.yaml_model.Service;

public class DeployTaskHandler implements TaskHandler {

    private static Logger logger = LoggerFactory.getLogger(DeployTaskHandler.class);

    private RapidoTemplate rapidoTemplate;
    private String serviceName;
    private List<Node> targetNodes;
    private String imageTag;
    private Boolean isRollback;

    public static final String LATEST = ":latest";

    public DeployTaskHandler(RapidoTemplate rapidoTemplate, String serviceName, List<Node> targetNodes, String imageTag,
            Boolean isRollback) {
        super();
        this.rapidoTemplate = rapidoTemplate;
        this.serviceName = serviceName;
        this.targetNodes = targetNodes;
        this.imageTag = imageTag;
        this.isRollback = isRollback;
    }

    @Override
    public void runTask() {
        RapidoLogCentre.printEmptyLine();
        RapidoLogCentre.printInCentreWithStar("Start service task: " + serviceName);
        logger.info("Get service task: {}, to build image-tag: {}, rapido will deploy this service to nodes: {}", serviceName, imageTag,
                targetNodes);
        // build image if need
        Service service = rapidoTemplate.getServices().get(serviceName);
        String imageName = service.getImage();
        String imageNameWithRepo = combineImageNameWithRepo(imageName);
        boolean isBuildImage = false;

        DeliveryType deliveryType = DeliveryType.getType(rapidoTemplate.getDelivery_type());
        if (isRollback) {
            imageName = combineImageNameWithRepoAndTag(imageName);
            isBuildImage = false;
        } else if (service.getBuild() != null) {
            if (imageTag == null) {
                if (!deliveryType.isOfficial()) {
                    imageTag = rapidoTemplate.getOwner();
                    isBuildImage = true;
                } else {
                    throw new IllegalImageTagsException("Image tag can not be empty when build is specific in official delivery");
                }
            } else {
                isBuildImage = true;
            }
        }

        DockerProxy optDocker = DockerProxyFactory.getInstance(rapidoTemplate.getRemote_docker());
        String existedImageId = optDocker.isImageExited(combineImageNameWithRepoAndTag(imageName));
        if (isBuildImage && existedImageId != null) {
            imageName = combineImageNameWithRepoAndTag(imageName);
            if (deliveryType.isOfficial()) {
                isBuildImage = false;
            } else {
                // remove if specific image has existed
                logger.info("Find local image with same image-tag: {}, rapido will try to remove it", imageName);
                optDocker.tryToRemoveImage(existedImageId);
            }
        }

        if (isBuildImage) {
            logger.info("Start to build image: {}", imageName);
            String imageId = optDocker.buildImage(service.getBuild(), imageName);
            logger.info("Building successfully, imageId is {}", imageId);
            logger.info("Start to push image");
            optDocker.pushImage(imageName, rapidoTemplate.getRepository().getUsername(), rapidoTemplate.getRepository().getPassword());
            if (deliveryType.isOfficial()) {
                logger.info("Start to tag and push image with latest tag");
                optDocker.tagImage(imageId, imageNameWithRepo, "latest");
                optDocker.pushImage(imageNameWithRepo + ":latest", rapidoTemplate.getRepository().getUsername(),
                        rapidoTemplate.getRepository().getPassword());
            }
            logger.info("Pushing successfully");
        }

        Deploy deploy = service.getDeploy();
        if (deploy == null) {
            logger.info("No deployment description found, deploy skipped");
            RapidoLogCentre.printInCentreWithStar("End service task");
            return;
        }

        imageName = imageName.contains(":") ? imageName : imageName + LATEST;
        logger.info("Rapido will use image {} to create containers of service {}", imageName, serviceName);

        // go to each node and do operation
        String deploy_policy = deploy.getDeploy_policy();
        DeployPolicy deployPolicy = DeployPolicy.getType(deploy_policy);
        logger.info("Deploy policy is {}", deployPolicy.getValue());
        for (Node node : targetNodes) {
            ProcessorInfo processorInfo = new ProcessorInfo(rapidoTemplate.getRepository(), deliveryType, rapidoTemplate.getOwner(), node,
                    deploy_policy, service, imageName);
            NodeProcessorFactory.getDeployProcessor(deployPolicy).process(processorInfo);
        }
        optDocker.tryToRemoveImage(existedImageId);
        RapidoLogCentre.printInCentreWithStar("End service task");
    }

    private String combineImageNameWithRepoAndTag(String imageName) {
        return rapidoTemplate.getRepository().repo() + "/" + imageName + ":" + imageTag;
    }

    private String combineImageNameWithRepo(String imageName) {
        return rapidoTemplate.getRepository().repo() + "/" + imageName;
    }
}