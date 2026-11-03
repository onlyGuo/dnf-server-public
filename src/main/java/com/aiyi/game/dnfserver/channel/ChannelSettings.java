package com.aiyi.game.dnfserver.channel;

public class ChannelSettings {
    private boolean enabled;
    private String mode;
    private String target;
    private Integer channelType;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public Integer getChannelType() { return channelType; }
    public void setChannelType(Integer channelType) { this.channelType = channelType; }
}
