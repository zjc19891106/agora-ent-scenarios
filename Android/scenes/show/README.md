# 秀场直播

> 本文档主要介绍如何快速跑通 <mark>秀场直播</mark> 示例工程

---

# 环境准备

- 最低兼容 Android 5.0（SDK API Level 21）
- Android Studio 3.5及以上版本。
- Android 5.0 及以上的手机设备。

---

## 运行示例

- <mark>1. </mark> 获取声网App ID -------- [声网Agora - 文档中心 - 如何获取 App ID](https://docs.agora.io/cn/Agora%20Platform/get_appid_token?platform=All%20Platforms#%E8%8E%B7%E5%8F%96-app-id)  
  
  > - 点击创建应用  
  >   
  >   ![xxx](image/SamplePicture2.png)  
  > 
  > - 选择你要创建的应用类型  
  >   
  >   ![xxx](image/SamplePicture3.png)  
  > 
  > - 得到App ID与App 证书  
  >   
  >   ![xxx](image/SamplePicture4.png)  
  
  获取App 证书 ----- [声网Agora - 文档中心 - 获取 App 证书](https://docs.agora.io/cn/Agora%20Platform/get_appid_token?platform=All%20Platforms#%E8%8E%B7%E5%8F%96-app-%E8%AF%81%E4%B9%A6) 

- <mark>2. </mark> 在项目的[**gradle.properties**](../../gradle.properties)里填写需要的声网 App ID 和 App证书  
  ![xxx](image/SamplePicture1.png)  
  
  ```texag-0-1gpap96h0ag-1-1gpap96h0ag-0-1gpap96h0ag-1-1gpap96h0ag-0-1gpap96h0ag-1-1gpap96h0ag-0-1gpap96h0ag-1-1gpap96h0ag-0-1gpap96h0ag-1-1gpap96h0
  AGORA_APP_ID：声网appid  
  AGORA_APP_CERTIFICATE：声网Certificate  
  ```

- <mark>3. </mark> 美颜配置
  
  美颜资源请联系商汤科技商务获取。
  
  ![xxx](image/SamplePicture5.png)
  
  > - 将STMobileJNI-release.aar放在**scenes/show/aars/STMobileJNI**目录下
  > 
  > - 将SenseArSourceManager-release.aar放在**scenes/show/aars/SenseArSourceManager**目录下
  > 
  > - 将SDK里的资源文件复制到**scenes/show/src/main/assets** 目录下。这个项目用到的资源文件列举如下：
  >   
  >   - license/SenseME.lic : 证书资源
  >   - models/*.model : AI等训练模型资源
  >   - sticker_face_shape/lianxingface.zip : 贴纸资源
  >   - style_lightly/*.zip : 风格妆资源

- <mark>4. </mark> 用 Android Studio 运行项目即可开始您的体验

---

## 运行或集成遇到困难，该如何联系声网获取协助

方案1：如果您已经在使用声网服务或者在对接中，可以直接联系对接的销售或服务；

方案2：发送邮件给 [support@agora.io](mailto:support@agora.io) 咨询

---

## 代码许可

示例项目遵守 MIT 许可证。

---

