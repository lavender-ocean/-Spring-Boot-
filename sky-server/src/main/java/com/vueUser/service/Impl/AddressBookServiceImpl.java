package com.vueUser.service.Impl;

import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;

import com.sky.exception.AddressBookBusinessException;
import com.vueUser.mapper.AddressBookMapper;
import com.vueUser.service.AddressBookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Slf4j
public class AddressBookServiceImpl implements AddressBookService {
    @Autowired
    private AddressBookMapper addressBookMapper;

    /**
     * 条件查询
     *
     * @param addressBook
     * @return
     */
    public List<AddressBook> list(AddressBook addressBook) {
        //强制只查看当前登陆用户的
        addressBook.setUserId(BaseContext.getCurrentId());
        return addressBookMapper.list(addressBook);
    }

    /**
     * 新增地址
     *
     * @param addressBook
     */
    public void save(AddressBook addressBook) {
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setIsDefault(0);

        log.info("新增地址{}", addressBook);


        addressBookMapper.insert(addressBook);
    }

    /**
     * 根据id查询
     *
     * @param id
     * @return
     */
    public AddressBook getById(Long id) {
        AddressBook addressBook = addressBookMapper.getById(id);
        if(addressBook == null){
            return null;
        }

        //越权校验
        Long currentId = BaseContext.getCurrentId();
        if(!addressBook.getUserId().equals(currentId)){
            log.warn("越权访问地址：addressId={}, ownerUserId={}, currentUserId={}",
                    id, addressBook.getUserId(), currentId);
            throw new AddressBookBusinessException("无权访问该地址");
        }
        return addressBook;
    }

    /**
     * 根据id修改地址
     *
     * @param addressBook
     */
    public void update(AddressBook addressBook) {
        // 越权校验
        AddressBook db = addressBookMapper.getById(addressBook.getId());
        if (db == null) {
            throw new AddressBookBusinessException("地址不存在");
        }
        Long currentId = BaseContext.getCurrentId();
        if (!db.getUserId().equals(currentId)) {
            log.warn("越权修改地址：addressId={}, ownerUserId={}, currentUserId={}",
                    addressBook.getId(), db.getUserId(), currentId);
            throw new AddressBookBusinessException("无权修改该地址");
        }

        // 防止前端改 userId 把地址转移到别人名下
        addressBook.setUserId(currentId);

        //这里才去进行更新地址
        addressBookMapper.update(addressBook);
    }

    /**
     * 设置默认地址
     *
     * @param addressBook
     */
    @Transactional
    public void setDefault(AddressBook addressBook) {

        Long currentId = BaseContext.getCurrentId();

        //越权校验
        AddressBook db = addressBookMapper.getById(addressBook.getId());
        if (db == null || !db.getUserId().equals(currentId)) {
            throw new AddressBookBusinessException("无权操作该地址");
        }

        //1、将当前用户的所有地址修改为非默认地址 update address_book set is_default = ? where user_id = ?
        addressBook.setIsDefault(0);
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBookMapper.updateIsDefaultByUserId(addressBook);

        //2、将当前地址改为默认地址 update address_book set is_default = ? where id = ?
        addressBook.setIsDefault(1);
        addressBookMapper.update(addressBook);
    }

    /**
     * 根据id删除地址
     *
     * @param id
     */
    public void deleteById(Long id) {

        //越权校验
        AddressBook db = addressBookMapper.getById(id);
        if (db == null) {
            throw new AddressBookBusinessException("地址不存在");
        }
        Long currentId = BaseContext.getCurrentId();
        if (!db.getUserId().equals(currentId)) {
            log.warn("越权删除地址：addressId={}, ownerUserId={}, currentUserId={}",
                    id, db.getUserId(), currentId);
            throw new AddressBookBusinessException("无权删除该地址");
        }

        addressBookMapper.deleteById(id);
    }

}
