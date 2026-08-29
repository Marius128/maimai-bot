package per.kokabot.entity;

import lombok.Data;
import per.kokabot.provider.MaimaiProviderType;

@Data
public class BotUserDO {

  private Long qqId;
  private MaimaiProviderType provider;
  private java.sql.Timestamp createdAt;
  private java.sql.Timestamp updatedAt;

}
