package com.sky.controller.admin;


import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;


/**
 * 本地文件管理
 */
@RestController
@RequestMapping("/admin/common")
@Slf4j
@Api(tags = "通用接口")
public class CommonController {

     //本地磁盘存储路径（★ 改为项目内相对路径：原值是本机绝对路径，含个人目录信息，已移除）
    private static final String Image_Path_3 = "./images/";

    //

    /**
     *
     * 文件上传
     * @param file
     * @return
     */
    @ApiOperation("")
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) throws IOException {

        log.info("执行到成功获取图片");

        //1.获取原始文件名字
        String originalFilename = file.getOriginalFilename();

        //2.截取文件后缀
        String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));

        //3.UUID生成新的文件名，防止重名覆盖  后缀还要了
        String newFileName = UUID.randomUUID().toString() + suffix;

        //4.判断文件夹是否存在 · 不存在就创建
        File folder = new File(Image_Path_3);
        if(!folder.exists()){
            folder.mkdirs();
        }

        //5.保存文件到磁盘
        File newFile = new File(Image_Path_3 + newFileName);
        file.transferTo(newFile);

        // 本地存储：返回给前端的访问地址（需要配置资源映射！！）
        String url = "/images/" + newFileName;

        log.info("url:{}",url);


        log.info("当前图片上传成功，并且存储在{}",newFileName);

        return Result.success(url);
    }








}
