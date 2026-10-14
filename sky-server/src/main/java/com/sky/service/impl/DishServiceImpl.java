package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.CacheKeyConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.entity.Setmeal;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.utils.TwoLevelCacheUtil;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DishServiceImpl implements DishService {

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    // 开始二级缓存
    @Autowired
    private TwoLevelCacheUtil twoLevelCacheUtil;
    //



    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveWithFlavor(DishDTO dishDTO) {

        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);

        //向菜品表插入数据
        dishMapper.insert(dish);


        //向口味表插入 多条数据

        Long id = dish.getId();

        List<DishFlavor> flavors = dishDTO.getFlavors();

        if (flavors != null && flavors.size() > 0) {

            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(id);
            });

            //插入数据
            dishFlavorMapper.insertBatch(flavors);



        }


        // 清理缓存：该分类的菜品列表
        twoLevelCacheUtil.delete(CacheKeyConstant.dishCategory(dishDTO.getCategoryId()));

    }

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */

    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {

        PageHelper.startPage(dishPageQueryDTO.getPage(),dishPageQueryDTO.getPageSize());

        //需要使用VO 给前端返回视图对象
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);


        return new PageResult(page.getTotal(),page.getResult());

    }
    /**
     * 菜品起售停售
     *
     * @param status
     * @param id
     */
    @Transactional
    public void startOrStop(Integer status, Long id) {
        Dish dish = Dish.builder()
                .id(id)
                .status(status)
                .build();
        dishMapper.update(dish);


        // 清理缓存：菜品详情 + 该分类的菜品列表
        Dish dbDish = dishMapper.getById(id);
        if (dbDish != null) {
            twoLevelCacheUtil.delete(CacheKeyConstant.dishInfo(id));
            twoLevelCacheUtil.delete(CacheKeyConstant.dishCategory(dbDish.getCategoryId()));
        }


        if (status == StatusConstant.DISABLE) {
            // 如果是停售操作，还需要将包含当前菜品的套餐也停售
            List<Long> dishIds = new ArrayList<>();
            dishIds.add(id);
            // select setmeal_id from setmeal_dish where dish_id in (?,?,?)
            List<Long> setmealIds = setmealDishMapper.getSetmealDishIdsByDishId(dishIds);
            if (setmealIds != null && setmealIds.size() > 0) {
                for (Long setmealId : setmealIds) {
                    Setmeal setmeal = Setmeal.builder()
                            .id(setmealId)
                            .status(StatusConstant.DISABLE)
                            .build();
                    setmealMapper.update(setmeal);


                    // 清理套餐缓存
                    twoLevelCacheUtil.delete(CacheKeyConstant.setmealInfo(setmealId));
                }



                // 清套餐分类缓存（分类 id 可以查，简化处理）
                twoLevelCacheUtil.deleteByPrefix("setmeal:category:");
            }
        }

    }



    /**
     *
     * 菜品批量删除
     * @param ids
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {

        //判断当前菜品是否可以删除？
        for(Long id:ids){
            Dish dish = dishMapper.getById(id);
            if(dish == null){
                continue; //数据已经不存在，跳过
            }
            if(dish.getStatus()== StatusConstant.ENABLE){
                throw new DeletionNotAllowedException("当前菜品处于起售中");
            }
        }


        //当前菜品是否被套餐关联？
        /**
         * 为什么是ids？ 因为一个菜品可以被多个 套餐 关联
         */
        List<Long> setmealDishIdsByDishIds = setmealDishMapper.getSetmealDishIdsByDishId(ids);
        if(setmealDishIdsByDishIds.size()>0 && setmealDishIdsByDishIds != null){
            //当前菜品被套餐关联了
            throw new DeletionNotAllowedException("当前菜品被套餐关联了");

        }
        //删除菜品表的菜品数据

//        for(Long id:ids){
//            dishMapper.deleteById(id);
//            //删除菜品关联的 口味表的 数据
//            dishFlavorMapper.deleteByDishId(id);
//        }

        //优化 批量删除


        // 关键：先查出所有菜品的分类 id（删完就查不到了）
        List<Dish> dishes = new ArrayList<>();
        for (Long id : ids) {
            Dish dish = dishMapper.getById(id);
            if (dish != null) {
                dishes.add(dish);
            }
        }

        dishMapper.deleteByIds(ids);

        dishFlavorMapper.deleteByDishIds(ids);


        // 清理缓存
        for (Dish dish : dishes) {
            twoLevelCacheUtil.delete(CacheKeyConstant.dishInfo(dish.getId()));
        }
        // 关键：必须先去重 categoryId（多个菜品可能同分类），否则重复删缓存：
        Set<Long> categoryIds = dishes.stream()
                .map(Dish::getCategoryId)
                .collect(Collectors.toSet());
        for (Long categoryId : categoryIds) {
            twoLevelCacheUtil.delete(CacheKeyConstant.dishCategory(categoryId));
        }

    }

    /**
     * 查菜品以及对应的口味
     * @param id
     * @return
     */
    @Override
    public DishVO getByIdWithFlavor(Long id) {

        //加入 二级缓存
        String key = CacheKeyConstant.dishInfo(id);

        return twoLevelCacheUtil.get(key,
                () -> {
                    Dish dish = dishMapper.getById(id);

                    if (dish == null) {
                        return null;
                    }

                    List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);

                    DishVO dishVO = new DishVO();

                    BeanUtils.copyProperties(dish,dishVO);
                    dishVO.setFlavors(dishFlavors);

                    return dishVO;

                });
        //
    }


    /**
     * 添加口味
     * @param dishDTO
     */
    @Override
    public void updatWithFlavor(DishDTO dishDTO) {

        // 先查原菜品（拿原分类 id）
        Dish oldDish = dishMapper.getById(dishDTO.getId());

        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);

        dishMapper.update(dish);

        dishFlavorMapper.deleteByDishId(dishDTO.getId());

        //先删除再插入

        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0) {
            // 补上循环设置dishId，dishId直接从dishDTO拿id
            flavors.forEach(flavor -> {
                flavor.setDishId(dishDTO.getId());
            });

            dishFlavorMapper.insertBatch(flavors);
        }

        // 清理缓存：菜品详情 + 原/新分类列表
        twoLevelCacheUtil.delete(CacheKeyConstant.dishInfo(dishDTO.getId()));
        if (oldDish != null) {
            twoLevelCacheUtil.delete(CacheKeyConstant.dishCategory(oldDish.getCategoryId()));
        }
        if (!dishDTO.getCategoryId().equals(oldDish == null ? null : oldDish.getCategoryId())) {
            // 分类变了，新分类也要清
            twoLevelCacheUtil.delete(CacheKeyConstant.dishCategory(dishDTO.getCategoryId()));
        }
    }

    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    @Override
    public List<DishVO> listWithFlavor(Dish dish) {
        List<Dish> dishList = dishMapper.list(dish);

        List<DishVO> dishVOList = new ArrayList<>();

        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);

            //根据菜品id查询对应的口味
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }

        return dishVOList;
    }


    /**
     * 根据分类id查询菜品
     * @param categoryId
     * @return
     */
    public List<Dish> list(Long categoryId) {
        Dish dish = Dish.builder()
                .categoryId(categoryId)
                .status(StatusConstant.ENABLE)
                .build();
        return dishMapper.list(dish);
    }



    /**
     * 根据分类 id 查询起售菜品（带口味）
     * 暂时无缓存（批次 2 会接入 TwoLevelCacheUtil）
     */
    @Override
    public List<DishVO> listWithFlavorByCategory(Long categoryId) {
        if (categoryId == null) {
            log.warn("categoryId 为 null，返回空列表");
            return new ArrayList<>();
        }
        //加入二级缓存
        String key = CacheKeyConstant.dishCategory(categoryId);
        return twoLevelCacheUtil.get(key,() -> {
            Dish dish = Dish.builder()
                    .categoryId(categoryId)
                    .status(StatusConstant.ENABLE)
                    .build();
            return listWithFlavor(dish);
        } );

    }



    @Override
    public boolean tryDeductStock(Long dishId, Integer count) {
        if (count == null || count <= 0) {
            return true;
        }
        int affected = dishMapper.deductStock(dishId, count);
        boolean success = affected > 0;
        if (!success) {
            log.warn("库存不足，扣减失败：dishId={}, count={}", dishId, count);
        }
        return success;
    }

    @Override
    public void rollbackStock(Long dishId, Integer count) {
        if (count == null || count <= 0) {
            return;
        }
        dishMapper.rollbackStock(dishId, count);
        log.info("库存已回滚：dishId={}, count={}", dishId, count);
    }


}
















