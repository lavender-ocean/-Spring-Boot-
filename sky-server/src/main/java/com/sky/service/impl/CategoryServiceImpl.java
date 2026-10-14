package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.CacheKeyConstant;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.entity.Dish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.CategoryService;
import com.sky.utils.TwoLevelCacheUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分类业务层
 */
@Service
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    // 开始二级缓存
    @Autowired
    private TwoLevelCacheUtil twoLevelCacheUtil;
    //

    /**
     * 新增分类
     * @param categoryDTO
     */
    public void save(CategoryDTO categoryDTO) {
        Category category = new Category();
        //属性拷贝
        BeanUtils.copyProperties(categoryDTO, category);

        //分类状态默认为禁用状态0
        category.setStatus(StatusConstant.DISABLE);

        //设置创建时间、修改时间、创建人、修改人
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());
        category.setCreateUser(BaseContext.getCurrentId());
        category.setUpdateUser(BaseContext.getCurrentId());

        categoryMapper.insert(category);

        //清理缓存       该 type 的分类列表
        twoLevelCacheUtil.delete(CacheKeyConstant.categoryList(category.getType()));
    }

    /**
     * 分页查询
     * @param categoryPageQueryDTO
     * @return
     */
    public PageResult pageQuery(CategoryPageQueryDTO categoryPageQueryDTO) {
        PageHelper.startPage(categoryPageQueryDTO.getPage(),categoryPageQueryDTO.getPageSize());
        //下一条sql进行分页，自动加入limit关键字分页
        Page<Category> page = categoryMapper.pageQuery(categoryPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 根据id删除分类
     * @param id
     */
    public void deleteById(Long id) {
        //查询当前分类是否关联了菜品，如果关联了就抛出业务异常
        Integer count = dishMapper.countByCategoryId(id);
        if(count > 0){
            //当前分类下有菜品，不能删除
            throw new DeletionNotAllowedException(MessageConstant.CATEGORY_BE_RELATED_BY_DISH);
        }

        //查询当前分类是否关联了套餐，如果关联了就抛出业务异常
        count = setmealMapper.countByCategoryId(id);
        if(count > 0){
            //当前分类下有菜品，不能删除
            throw new DeletionNotAllowedException(MessageConstant.CATEGORY_BE_RELATED_BY_SETMEAL);
        }

        // 先删除的话 就拿不到category 了

        // 先查出来拿 type
        Category category = categoryMapper.getById(id);

        //删除分类数据
        categoryMapper.deleteById(id);


        // 清缓存
        if (category != null) {
            twoLevelCacheUtil.delete(CacheKeyConstant.categoryList(category.getType()));
        }
    }

    /**
     * 修改分类
     * @param categoryDTO
     */
    public void update(CategoryDTO categoryDTO) {
        Category category = new Category();
        BeanUtils.copyProperties(categoryDTO,category);

        //设置修改时间、修改人
        category.setUpdateTime(LocalDateTime.now());
        category.setUpdateUser(BaseContext.getCurrentId());

        categoryMapper.update(category);

        // 清理缓存：该 type 的分类列表
        twoLevelCacheUtil.delete(CacheKeyConstant.categoryList(category.getType()));

        // 关键：分类名改了，所有菜品缓存里的 categoryName 都过期了
        // 简单粗暴：清所有菜品缓存
        twoLevelCacheUtil.deleteByPrefix("dish:");
        log.info("分类修改，已清所有菜品缓存");
    }

    /**
     * 启用、禁用分类
     * @param status
     * @param id
     */
    @Transactional
    public void startOrStop(Integer status, Long id) {


        // 1. 先查原分类（拿 type）
        Category dbCategory = categoryMapper.getById(id);
        if (dbCategory == null) {
            log.warn("分类不存在：{}", id);
            return;
        }

        // 2. 更新分类状态
        Category category = Category.builder()
                .id(id)
                .status(status)
                .updateTime(LocalDateTime.now())
                .updateUser(BaseContext.getCurrentId())
                .build();
        categoryMapper.update(category);

        // 3. 关键：禁用分类时，级联停售该分类下的菜品
        if (status == StatusConstant.DISABLE && dbCategory.getType() == 1) {
            // 只对"菜品分类"（type=1）联动，套餐分类（type=2）不处理
            dishMapper.updateStatusByCategoryId(id, StatusConstant.DISABLE);
            log.info("分类 {} 已禁用，级联停售其下所有菜品", id);

            // 清该分类下所有菜品的详情缓存（dish:info:{id}）
            List<Dish> dishes = dishMapper.list(Dish.builder().categoryId(id).build());
            for (Dish dish : dishes) {
                twoLevelCacheUtil.delete(CacheKeyConstant.dishInfo(dish.getId()));
            }
        }

        // 4. 清分类列表缓存
        twoLevelCacheUtil.delete(CacheKeyConstant.categoryList(dbCategory.getType()));

        // 5. 清该分类的菜品列表缓存
        if (dbCategory.getType() == 1) {
            twoLevelCacheUtil.delete(CacheKeyConstant.dishCategory(id));
        }


    }

    /**
     * 根据类型查询分类
     * @param type
     * @return
     */
    public List<Category> list(Integer type) {

        // type 为 null 时不缓存（避免生成 category:list:null）
        if (type == null) {
            return categoryMapper.list(null);
        }
        //使用 缓存key 命名规范
        String key = CacheKeyConstant.categoryList(type);

        return twoLevelCacheUtil.get(key, () -> categoryMapper.list(type));
    }
}











