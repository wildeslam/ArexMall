package com.macro.mall.portal.service.impl;

import cn.hutool.core.date.DateUtil;
import com.github.pagehelper.PageHelper;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.mapper.PmsBrandMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.PmsBrand;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsProductExample;
import com.macro.mall.portal.constants.RedisConstant;
import com.macro.mall.portal.dao.HomeDao;
import com.macro.mall.portal.service.PmsPortalBrandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 前台品牌管理Service实现类
 * Created by macro on 2020/5/15.
 */
@Service
public class PmsPortalBrandServiceImpl implements PmsPortalBrandService {
    @Autowired
    private HomeDao homeDao;
    @Autowired
    private PmsBrandMapper brandMapper;
    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private RedisService redisService;

    private static final Long APPLE_BRAND_ID = 60L;

    @Override
    public List<PmsBrand> recommendList(Integer pageNum, Integer pageSize) {
        int offset = (pageNum - 1) * pageSize;
        return homeDao.getRecommendBrandList(offset, pageSize);
    }

    @Override
    public PmsBrand detail(Long brandId) {
      PmsBrand brand = brandMapper.selectByPrimaryKey(brandId);
//      String today = DateUtil.format(new Date(), "yyyy-MM-dd");
//      brand.setTodaySales((Long) redisService.get(String.format(RedisConstant.BRAND_SALES_KEY, today,
//        brand.getName())));
      return  brand;
    }

    @Override
    public CommonPage<PmsProduct> productList(Long brandId, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum,pageSize);
        PmsProductExample example = new PmsProductExample();
        example.createCriteria().andDeleteStatusEqualTo(0)
                .andPublishStatusEqualTo(1)
                .andBrandIdEqualTo(brandId);
        List<PmsProduct> productList = productMapper.selectByExample(example);
        // 10% discount on Apple products
        discountForAppleProduct(productList, 0.9);
        return CommonPage.restPage(productList);
    }

    private void discountForAppleProduct(List<PmsProduct> productList, double rate) {
      for (PmsProduct product : productList) {
        if (Objects.equals(product.getBrandId(), APPLE_BRAND_ID)) {
          product.setPrice(product.getPrice().multiply(new BigDecimal(rate)));
        }
      }
    }
}
