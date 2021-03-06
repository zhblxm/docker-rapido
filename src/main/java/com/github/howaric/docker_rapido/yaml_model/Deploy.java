package com.github.howaric.docker_rapido.yaml_model;

import com.github.howaric.docker_rapido.core.DeployPolicy;
import org.apache.commons.lang.builder.ToStringBuilder;

import javax.validation.constraints.Min;

public class Deploy {

    private Placement placement;

    // options: rolling-update, force-update, on-absence(default)
    private String deploy_policy = DeployPolicy.ON_ABSENCE.getValue();

    private Integer stop_timeout;

    private RestartPolicy restart_policy = new RestartPolicy();

    @Min(message = "replicas must be at least 1", value = 1)
    private Integer replicas = 1;

    private HealthCheck healthcheck = new HealthCheck();

    private ServiceCheck servicecheck = new ServiceCheck();

    public RestartPolicy getRestart_policy() {
        return restart_policy;
    }

    public void setRestart_policy(RestartPolicy restart_policy) {
        this.restart_policy = restart_policy;
    }

    public Placement getPlacement() {
        return placement;
    }

    public void setPlacement(Placement placement) {
        this.placement = placement;
    }

    public Integer getStop_timeout() {
        return stop_timeout;
    }

    public void setStop_timeout(Integer stop_timeout) {
        this.stop_timeout = stop_timeout;
    }

    public String getDeploy_policy() {
        return deploy_policy;
    }

    public void setDeploy_policy(String deploy_policy) {
        this.deploy_policy = deploy_policy;
    }

    public Integer getReplicas() {
        return replicas;
    }

    public void setReplicas(Integer replicas) {
        this.replicas = replicas;
    }

    public HealthCheck getHealthcheck() {
        return healthcheck;
    }

    public void setHealthcheck(HealthCheck healthcheck) {
        this.healthcheck = healthcheck;
    }

    public ServiceCheck getServicecheck() {
        return servicecheck;
    }

    public void setServicecheck(ServiceCheck servicecheck) {
        this.servicecheck = servicecheck;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("placement", placement).append("deploy_policy", deploy_policy)
                .append("stop_timeout", stop_timeout).append("restart_policy", restart_policy).append("replicas", replicas)
                .append("healthcheck", healthcheck).append("servicecheck", servicecheck).toString();
    }

}
