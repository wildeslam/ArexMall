package com.macro.mall.portal.service.impl;

import com.macro.mall.common.enums.PromotionTypeEnum;
import com.macro.mall.model.OmsCartItem;
import com.macro.mall.model.PmsProductFullReduction;
import com.macro.mall.model.PmsProductLadder;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.domain.PromotionProduct;
import com.macro.mall.portal.service.OmsPromotionService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by macro on 2018/8/27.
 * 促销管理Service实现类
 */
@Service
public class OmsPromotionServiceImpl implements OmsPromotionService {
    @Autowired
    private PortalProductDao portalProductDao;

    @Override
    public List<CartPromotionItem> calcCartPromotion(List<OmsCartItem> cartItemList) {
        //1.先根据productId对CartItem进行分组，以spu为单位进行计算优惠
        Map<Long, List<OmsCartItem>> productCartMap = groupCartItemBySpu(cartItemList);
        //2.查询所有商品的优惠相关信息
        List<PromotionProduct> promotionProductList = getPromotionProductList(cartItemList);
        //3.根据商品促销类型计算商品促销优惠价格
        List<CartPromotionItem> cartPromotionItemList = new ArrayList<>();
        for (Map.Entry<Long, List<OmsCartItem>> entry : productCartMap.entrySet()) {
            Long productId = entry.getKey();
            PromotionProduct promotionProduct = getPromotionProductById(productId, promotionProductList);
            List<OmsCartItem> itemList = entry.getValue();
            Integer promotionType = promotionProduct.getPromotionType();
            PromotionTypeEnum promotionTypeEnum = PromotionTypeEnum.of(promotionType);
            switch (promotionTypeEnum) {
              case PROMOTION_PRICE:
                //单品促销
                for (OmsCartItem item : itemList) {
                  CartPromotionItem cartPromotionItem = new CartPromotionItem();
                  BeanUtils.copyProperties(item,cartPromotionItem);
                  cartPromotionItem.setPromotionMessage("单品促销");
                  //商品原价-促销价
                  PmsSkuStock skuStock = getOriginalPrice(promotionProduct, item.getProductSkuId());
                  BigDecimal originalPrice = skuStock.getPrice();
                  //单品促销使用原价
                  cartPromotionItem.setPrice(originalPrice);
                  cartPromotionItem.setReduceAmount(originalPrice.subtract(skuStock.getPromotionPrice()));
                  cartPromotionItem.setRealStock(skuStock.getStock()-skuStock.getLockStock());
                  cartPromotionItem.setIntegration(promotionProduct.getGiftPoint());
                  cartPromotionItem.setGrowth(promotionProduct.getGiftGrowth());
                  cartPromotionItemList.add(cartPromotionItem);
                }
                break;
              case LADDER_PRICE:
                //打折优惠
                int count = getCartItemCount(itemList);
                PmsProductLadder ladder = getProductLadder(count, promotionProduct.getProductLadderList());
                if(ladder!=null){
                  for (OmsCartItem item : itemList) {
                    CartPromotionItem cartPromotionItem = new CartPromotionItem();
                    BeanUtils.copyProperties(item,cartPromotionItem);
                    String message = getLadderPromotionMessage(ladder);
                    cartPromotionItem.setPromotionMessage(message);
                    //商品原价-折扣*商品原价
                    PmsSkuStock skuStock = getOriginalPrice(promotionProduct,item.getProductSkuId());
                    BigDecimal originalPrice = skuStock.getPrice();
                    BigDecimal reduceAmount = originalPrice.subtract(ladder.getDiscount().multiply(originalPrice));
                    cartPromotionItem.setReduceAmount(reduceAmount);
                    cartPromotionItem.setRealStock(skuStock.getStock()-skuStock.getLockStock());
                    cartPromotionItem.setIntegration(promotionProduct.getGiftPoint());
                    cartPromotionItem.setGrowth(promotionProduct.getGiftGrowth());
                    cartPromotionItemList.add(cartPromotionItem);
                  }
                }else{
                  handleNoReduce(cartPromotionItemList,itemList,promotionProduct);
                }
                break;
              case FULL_REDUCTION:
                //满减改成每满减
                //满减
                BigDecimal totalAmount = getCartItemAmount(itemList,promotionProductList);
                PmsProductFullReduction fullReduction = getProductFullReduction(totalAmount,promotionProduct.getProductFullReductionList());
                if (fullReduction != null) {
                  for (OmsCartItem item : itemList) {
                    CartPromotionItem cartPromotionItem = new CartPromotionItem();
                    BeanUtils.copyProperties(item, cartPromotionItem);
                    String message = getFullReductionPromotionMessage(fullReduction);
                    cartPromotionItem.setPromotionMessage(message);
                    //(商品原价/总价)*满减金额
                    //(商品原价)
                    PmsSkuStock skuStock = getOriginalPrice(promotionProduct, item.getProductSkuId());
                    assert skuStock != null;
                    BigDecimal originalPrice = skuStock.getPrice();
                    BigDecimal reduceAmount = originalPrice.divide(totalAmount,RoundingMode.HALF_EVEN)
                      .multiply(calculateFullReduction(originalPrice, fullReduction));
                    cartPromotionItem.setReduceAmount(reduceAmount);
                    cartPromotionItem.setRealStock(skuStock.getStock()-skuStock.getLockStock());
                    cartPromotionItem.setIntegration(promotionProduct.getGiftPoint());
                    cartPromotionItem.setGrowth(promotionProduct.getGiftGrowth());
                    cartPromotionItemList.add(cartPromotionItem);
                  }
                }else{
                  handleNoReduce(cartPromotionItemList,itemList,promotionProduct);
                }
                break;
              default:
                handleNoReduce(cartPromotionItemList, itemList,promotionProduct);
            }
        }
        return cartPromotionItemList;
    }

    /**
     * 查询所有商品的优惠相关信息
     */
    private List<PromotionProduct> getPromotionProductList(List<OmsCartItem> cartItemList) {
        List<Long> productIdList = new ArrayList<>();
        for(OmsCartItem cartItem:cartItemList){
            productIdList.add(cartItem.getProductId());
        }
        return portalProductDao.getPromotionProductList(productIdList);
    }

    /**
     * 以spu为单位对购物车中商品进行分组
     */
    private Map<Long, List<OmsCartItem>> groupCartItemBySpu(List<OmsCartItem> cartItemList) {
        Map<Long, List<OmsCartItem>> productCartMap = new TreeMap<>();
        for (OmsCartItem cartItem : cartItemList) {
            List<OmsCartItem> productCartItemList = productCartMap.get(cartItem.getProductId());
            if (productCartItemList == null) {
                productCartItemList = new ArrayList<>();
                productCartItemList.add(cartItem);
                productCartMap.put(cartItem.getProductId(), productCartItemList);
            } else {
                productCartItemList.add(cartItem);
            }
        }
        return productCartMap;
    }

    /**
     * 获取满减促销消息
     */
    private String getFullReductionPromotionMessage(PmsProductFullReduction fullReduction) {
        StringBuilder sb = new StringBuilder();
        sb.append("Full Reduction: ");
        sb.append("Reduce $");
        sb.append(fullReduction.getReducePrice());
        sb.append(" for every $");
        sb.append(fullReduction.getFullPrice());
        return sb.toString();
    }

    /**
     * 对没满足优惠条件的商品进行处理
     */
    private void handleNoReduce(List<CartPromotionItem> cartPromotionItemList, List<OmsCartItem> itemList,PromotionProduct promotionProduct) {
        for (OmsCartItem item : itemList) {
            CartPromotionItem cartPromotionItem = new CartPromotionItem();
          BeanUtils.copyProperties(item, cartPromotionItem);
          cartPromotionItem.setPromotionMessage("No Reduction");
          cartPromotionItem.setReduceAmount(new BigDecimal(0));
            PmsSkuStock skuStock = getOriginalPrice(promotionProduct,item.getProductSkuId());
            if(skuStock!=null){
                cartPromotionItem.setRealStock(skuStock.getStock()-skuStock.getLockStock());
            }
            cartPromotionItem.setIntegration(promotionProduct.getGiftPoint());
            cartPromotionItem.setGrowth(promotionProduct.getGiftGrowth());
            cartPromotionItemList.add(cartPromotionItem);
        }
    }

    private PmsProductFullReduction getProductFullReduction(BigDecimal totalAmount,List<PmsProductFullReduction> fullReductionList) {
        //按优惠的金额数从高到低排序
        fullReductionList.sort(new Comparator<PmsProductFullReduction>() {
            @Override
            public int compare(PmsProductFullReduction o1, PmsProductFullReduction o2) {
                return calculateFullReduction(totalAmount, o2).subtract(calculateFullReduction(totalAmount, o1)).intValue();
                //return o2.getFullPrice().subtract(o1.getFullPrice()).intValue();
            }
        });
        // 返回一个优惠力度最大的折扣
        for(PmsProductFullReduction fullReduction:fullReductionList){
            if(totalAmount.subtract(fullReduction.getFullPrice()).intValue()>=0){
                return fullReduction;
            }
        }
        return null;
    }

  /**
   * 计算每满减的优惠金额.
   */
    private BigDecimal calculateFullReduction(BigDecimal originPrice, PmsProductFullReduction reduction) {
      BigDecimal reductionAmount = new BigDecimal(0);
      BigDecimal newTotalAmount = BigDecimal.valueOf(originPrice.doubleValue());
      while (newTotalAmount.subtract(reduction.getFullPrice()).intValue() >= 0) {
        reductionAmount = reductionAmount.add(reduction.getReducePrice());
        newTotalAmount = newTotalAmount.subtract(reduction.getFullPrice());
      }
      return reductionAmount;
    }

    /**
     * 获取打折优惠的促销信息
     */
    private String getLadderPromotionMessage(PmsProductLadder ladder) {
        StringBuilder sb = new StringBuilder();
        sb.append("打折优惠：");
        sb.append("满");
        sb.append(ladder.getCount());
        sb.append("件，");
        sb.append("打");
        sb.append(ladder.getDiscount().multiply(new BigDecimal(10)));
        sb.append("折");
        return sb.toString();
    }

    /**
     * 根据购买商品数量获取满足条件的打折优惠策略
     */
    private PmsProductLadder getProductLadder(int count, List<PmsProductLadder> productLadderList) {
        //按数量从大到小排序
        productLadderList.sort(new Comparator<PmsProductLadder>() {
            @Override
            public int compare(PmsProductLadder o1, PmsProductLadder o2) {
                return o2.getCount() - o1.getCount();
            }
        });
        for (PmsProductLadder productLadder : productLadderList) {
            if (count >= productLadder.getCount()) {
                return productLadder;
            }
        }
        return null;
    }

    /**
     * 获取购物车中指定商品的数量
     */
    private int getCartItemCount(List<OmsCartItem> itemList) {
        int count = 0;
        for (OmsCartItem item : itemList) {
            count += item.getQuantity();
        }
        return count;
    }

    /**
     * 获取购物车中指定商品的总价
     */
    private BigDecimal getCartItemAmount(List<OmsCartItem> itemList, List<PromotionProduct> promotionProductList) {
        BigDecimal amount = new BigDecimal(0);
        for (OmsCartItem item : itemList) {
          //计算出商品原价
          PromotionProduct promotionProduct = getPromotionProductById(item.getProductId(), promotionProductList);
          if (promotionProduct != null) {
            PmsSkuStock skuStock = getOriginalPrice(promotionProduct, item.getProductSkuId());
            if (skuStock != null) {
              amount = amount.add(skuStock.getPrice().multiply(new BigDecimal(item.getQuantity())));

            }
          }
        }
        return amount;
    }

    /**
     * 获取商品的原价
     */
    private PmsSkuStock getOriginalPrice(PromotionProduct promotionProduct, Long productSkuId) {
        for (PmsSkuStock skuStock : promotionProduct.getSkuStockList()) {
            if (productSkuId.equals(skuStock.getId())) {
                return skuStock;
            }
        }
        return null;
    }

    /**
     * 根据商品id获取商品的促销信息
     */
    private PromotionProduct getPromotionProductById(Long productId, List<PromotionProduct> promotionProductList) {
        for (PromotionProduct promotionProduct : promotionProductList) {
            if (productId.equals(promotionProduct.getId())) {
                return promotionProduct;
            }
        }
        return null;
    }
}
