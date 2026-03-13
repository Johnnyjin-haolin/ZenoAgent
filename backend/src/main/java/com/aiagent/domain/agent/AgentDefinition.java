package com.aiagent.domain.agent;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
@Data
public class AgentDefinition {
  private String id;
  private String name;
  private String description;
  private String systemPrompt;
  private ToolsConfig tools = new ToolsConfig();
  @Data
  public static class ToolsConfig {
    private List<String> mcpGroups = new ArrayList<>();
    private List<String> systemTools = new ArrayList<>();
  }
}
