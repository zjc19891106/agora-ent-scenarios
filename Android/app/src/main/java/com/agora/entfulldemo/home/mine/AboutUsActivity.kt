package com.agora.entfulldemo.home.mine

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.agora.entfulldemo.databinding.AppActivityAboutUsBinding
import com.alibaba.android.arouter.facade.annotation.Route
import io.agora.scene.base.PagePathConstant
import io.agora.scene.base.component.BaseViewBindingActivity
import io.agora.scene.base.manager.PagePilotManager

@Route(path = PagePathConstant.pageMineAboutUs)
class AboutUsActivity : BaseViewBindingActivity<AppActivityAboutUsBinding>() {

    private val servicePhone = "400-632-6626"
    private val webSite = "https://www.agora.io/cn/about-us/"


    override fun getViewBinding(inflater: LayoutInflater): AppActivityAboutUsBinding {
        return AppActivityAboutUsBinding.inflate(inflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupClickPhoneAction()
        setupClickWebAction()
        // set versions
        binding.tvVersion.text = "#2.1.0"
        binding.tvServiceNumber.text = servicePhone
        binding.tvHomeWebSite.text = webSite
        binding.tvChatRoomVersion.text = "1.0.1"
        binding.tvSpaceVoiceVersion.text = "2.0.1"
        binding.tvOnlineKTVVersion.text = "3.0.1"
        binding.tvLiveShowVersion.text = "4.0.1"
        binding.tvGameRoomVersion.text = "5.0.1"
    }

    fun setupClickWebAction() {
        binding.vHomeWebPage.setOnClickListener {
            PagePilotManager.pageWebView(webSite)
        }
    }

    fun setupClickPhoneAction() {
        binding.vServicePhone.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CALL_PHONE),1)
            } else {
                val dialog = CallPhoneDialog().apply {
                    arguments = Bundle().apply {
                        putString(CallPhoneDialog.KEY_PHONE, servicePhone)
                    }
                }
                dialog.onClickCallPhone = {
                    val intent = Intent(Intent.ACTION_CALL)
                    val uri = Uri.parse("tel:" + servicePhone)
                    intent.setData(uri)
                    startActivity(intent)
                }
                dialog.show(supportFragmentManager, "CallPhoneDialog")
            }
        }
    }
}