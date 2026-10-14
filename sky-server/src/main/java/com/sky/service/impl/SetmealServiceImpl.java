package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.CacheKeyConstant;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.utils.TwoLevelCacheUtil;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;

    //开启 二级缓存
    @Autowired
    private TwoLevelCacheUtil twoLevelCacheUtil;
    //

    /**
     * 分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        int pageNum = setmealPageQueryDTO.getPage();
        int pageSize = setmealPageQueryDTO.getPageSize();

        PageHelper.startPage(pageNum, pageSize);
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 批量删除套餐
     * @param ids
     */
    @Transactional
    public void deleteBatch(List<Long> ids) {

        // 先查出所有套餐的分类 id  为后面的清理缓存做准备
        Set<Long> categoryIds = new HashSet<>();
        for (Long id : ids) {
            Setmeal setmeal = setmealMapper.getById(id);
            if (setmeal != null) {
                if (StatusConstant.ENABLE == setmeal.getStatus()) {
                    throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
                }
                categoryIds.add(setmeal.getCategoryId());
            }
        }

        ids.forEach(id -> {
            Setmeal setmeal = setmealMapper.getById(id);
            if(StatusConstant.ENABLE == setmeal.getStatus()){
                //起售中的套餐不能删除
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        });

        //删除 DB
        ids.forEach(setmealId -> {
            //删除套餐表中的数据
            setmealMapper.deleteById(setmealId);
            //删除套餐菜品关系表中的数据
            setmealDishMapper.deleteBySetmealId(setmealId);

            // 清套餐详情缓存
            twoLevelCacheUtil.delete(CacheKeyConstant.setmealInfo(setmealId));
        });

        // 清分类列表缓存
        categoryIds.forEach(cid ->
                twoLevelCacheUtil.delete(CacheKeyConstant.setmealCategory(cid)));
    }

    /**
     * 根据id查询套餐和套餐菜品关系
     *
     * @param id
     * @return
     */
    public SetmealVO getByIdWithDish(Long id) {

        // id 为 null 直接返回 null，不查缓存
        if (id == null) {
            log.warn("setmeal id 为 null，直接返回 null");
            return null;
        }

        String key = CacheKeyConstant.setmealInfo(id);

        return twoLevelCacheUtil.get(key,
                () -> {
                    Setmeal setmeal = setmealMapper.getById(id);
                    if(setmeal == null){
                        return null;
                    }
                    List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(id);

                    SetmealVO setmealVO = new SetmealVO();
                    BeanUtils.copyProperties(setmeal, setmealVO);
                    setmealVO.setSetmealDishes(setmealDishes);

                    return setmealVO;

                });


    }

    /**
     * 修改套餐
     *
     * @param setmealDTO
     */
    @Transactional
    public void update(SetmealDTO setmealDTO) {

        // 先查原套餐（拿原分类 id） 为清理缓存做准备
        Setmeal oldSetmeal = setmealMapper.getById(setmealDTO.getId());


        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        //1、修改套餐表，执行update
        setmealMapper.update(setmeal);

        //套餐id
        Long setmealId = setmealDTO.getId();

        //2、删除套餐和菜品的关联关系，操作setmeal_dish表，执行delete
        setmealDishMapper.deleteBySetmealId(setmealId);


        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && !setmealDishes.isEmpty()) {
            for (SetmealDish sd : setmealDishes) {
                sd.setSetmealId(setmealId);
                // 补全
                if (sd.getDishId() != null) {
                    Dish dish = dishMapper.getById(sd.getDishId());
                    if (dish != null) {
                        sd.setName(dish.getName());
                        sd.setPrice(dish.getPrice());
                    }
                }
            }
            setmealDishMapper.insertBatch(setmealDishes);
        }


//        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
//        setmealDishes.forEach(setmealDish -> {
//            setmealDish.setSetmealId(setmealId);
//        });
//        //3、重新插入套餐和菜品的关联关系，操作setmeal_dish表，执行insert
//        setmealDishMapper.insertBatch(setmealDishes);



        // 清理缓存
        twoLevelCacheUtil.delete(CacheKeyConstant.setmealInfo(setmealId));
        if (oldSetmeal != null) {
            twoLevelCacheUtil.delete(CacheKeyConstant.setmealCategory(oldSetmeal.getCategoryId()));
        }
        if (!setmealDTO.getCategoryId().equals(
                oldSetmeal == null ? null : oldSetmeal.getCategoryId())
        ) {
            twoLevelCacheUtil.delete(CacheKeyConstant.setmealCategory(setmealDTO.getCategoryId()));
        }


    }


    /**
     * 套餐起售、停售
     * @param status
     * @param id
     */
    public void startOrStop(Integer status, Long id) {
        //起售套餐时，判断套餐内是否有停售菜品，有停售菜品提示"套餐内包含未启售菜品，无法启售"
        if(status == StatusConstant.ENABLE){
            //select a.* from dish a left join setmeal_dish b on a.id = b.dish_id where b.setmeal_id = ?
            List<Dish> dishList = dishMapper.getBySetmealId(id);
            if(dishList != null && dishList.size() > 0){
                dishList.forEach(dish -> {
                    if(StatusConstant.DISABLE == dish.getStatus()){
                        throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                    }
                });
            }
        }

        Setmeal setmeal = Setmeal.builder()
                .id(id)
                .status(status)
                .build();
        setmealMapper.update(setmeal);

        // 清理缓存
        Setmeal dbSetmeal = setmealMapper.getById(id);
        if (dbSetmeal != null) {
            twoLevelCacheUtil.delete(CacheKeyConstant.setmealInfo(id));
            twoLevelCacheUtil.delete(CacheKeyConstant.setmealCategory(dbSetmeal.getCategoryId()));
        }
    }





    /**
     * 新增套餐，同时需要保存套餐和菜品的关联关系
     * @param setmealDTO
     */
    @Transactional
    public void saveWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        //向套餐表插入数据
        setmealMapper.insert(setmeal);

        //获取生成的套餐id
        Long setmealId = setmeal.getId();

//        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
//        setmealDishes.forEach(setmealDish -> {
//            setmealDish.setSetmealId(setmealId);
//        });
//
//        //保存套餐和菜品的关联关系
//        setmealDishMapper.insertBatch(setmealDishes);

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && !setmealDishes.isEmpty()) {
            for (SetmealDish sd : setmealDishes) {
                sd.setSetmealId(setmealId);
                // 从 dish 表补全冗余字段
                if (sd.getDishId() != null) {
                    Dish dish = dishMapper.getById(sd.getDishId());
                    if (dish != null) {
                        sd.setName(dish.getName());
                        sd.setPrice(dish.getPrice());
                    }
                }
            }
            setmealDishMapper.insertBatch(setmealDishes);
        }

        // 清理缓存
        twoLevelCacheUtil.delete(CacheKeyConstant.setmealCategory(setmealDTO.getCategoryId()));
    }



    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    public List<Setmeal> list(Setmeal setmeal) {
        // 只有按 categoryId 查的场景才缓存（C 端典型场景）
        if (setmeal.getCategoryId() == null) {
            return setmealMapper.list(setmeal);
        }

        String key = CacheKeyConstant.setmealCategory(setmeal.getCategoryId());

        return twoLevelCacheUtil.get(key,
                () -> {

                    List<Setmeal> list = setmealMapper.list(setmeal);
                    return list;
                });

    }


    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    public List<DishItemVO> getDishItemById(Long id) {

        return setmealMapper.getDishItemBySetmealId(id);
    }




}
