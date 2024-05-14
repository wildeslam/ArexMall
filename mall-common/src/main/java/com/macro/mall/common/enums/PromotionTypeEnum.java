package com.macro.mall.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author wildeslam.
 * @create 2024/5/13 20:36
 */
@AllArgsConstructor
public enum PromotionTypeEnum {
  UNKNOWN(-1, "未知"),
  NO_PROMOTION(0, "没有优惠"),
  PROMOTION_PRICE(1, "优惠价"),
  VIP_PRICE(2, "会员价"),
  LADDER_PRICE(3, "阶梯价格"),
  FULL_REDUCTION(4, "满减优惠"),
  TIME_LIMITED(5, "限时购")
  ;

  @Getter
  private final int code;

  @Getter
  private final String desc;

  public static PromotionTypeEnum of(int value) {
    for (PromotionTypeEnum type : PromotionTypeEnum.values()) {
      if (value == type.getCode()) {
        return type;
      }
    }
    return UNKNOWN;
  }
}
