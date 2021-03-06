package io.github.biezhi.makemoney.controller;

import com.blade.ioc.annotation.Inject;
import com.blade.kit.IOKit;
import com.blade.kit.PasswordKit;
import com.blade.mvc.annotation.*;
import com.blade.mvc.http.Request;
import com.blade.mvc.http.Response;
import com.blade.mvc.http.Session;
import com.blade.mvc.ui.RestResponse;
import io.github.biezhi.anima.Anima;
import io.github.biezhi.anima.page.Page;
import io.github.biezhi.makemoney.bootstrap.Bootstrap;
import io.github.biezhi.makemoney.bootstrap.Constant;
import io.github.biezhi.makemoney.entities.model.Option;
import io.github.biezhi.makemoney.entities.model.Order;
import io.github.biezhi.makemoney.entities.model.Template;
import io.github.biezhi.makemoney.entities.param.InstallParam;
import io.github.biezhi.makemoney.entities.param.OrderParam;
import io.github.biezhi.makemoney.entities.param.PasswordParam;
import io.github.biezhi.makemoney.entities.param.TemplateParam;
import io.github.biezhi.makemoney.service.MakeMoneyService;
import io.github.biezhi.makemoney.thirdparty.PayApi;
import io.github.biezhi.makemoney.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static io.github.biezhi.anima.Anima.select;
import static io.github.biezhi.anima.Anima.update;

/**
 * 后台管理页面
 *
 * @author biezhi
 * @date 2018/9/28
 */
@Path("admin")
public class AdminController {

    @Inject
    private MakeMoneyService makeMoneyService;

    @GetRoute("index")
    public String index() {
        return "admin/index.html";
    }

    @GetRoute("config")
    @JSON
    public Map<String, String> config() {
        Bootstrap.GLOBAL_CONFIG.put("theme", IndexController.THEME_NAME);
        return Bootstrap.GLOBAL_CONFIG;
    }

    @GetRoute("template/:theme")
    public String template(@PathParam String theme, Request request) throws IOException {
        File           themeDir = new File(Utils.CLASSPATH + "templates/themes/" + theme);
        File[]         files    = themeDir.listFiles(pathname -> pathname.getName().endsWith(".html"));
        List<Template> list     = new ArrayList<>();
        for (File file : Objects.requireNonNull(files)) {
            list.add(new Template(file.getName(), IOKit.readToString(file.toPath())));
        }
        request.attribute("files", list);
        request.attribute("theme", theme);
        return "admin/template.html";
    }

    @PostRoute("template_content")
    @JSON
    public String templateContent(@BodyParam TemplateParam template) throws IOException {
        List<String> lines   = Files.readAllLines(Paths.get(Utils.CLASSPATH + "templates/themes/" + template.getTheme() + "/" + template.getFileName()));
        String       collect = String.join("\n", lines);
        return collect;
    }

    @PostRoute("template")
    @JSON
    public RestResponse updateTemplate(@BodyParam TemplateParam templateParam) throws IOException {
        java.nio.file.Path file = Paths.get(Utils.CLASSPATH + "templates/themes/" + templateParam.getTheme() + "/" + templateParam.getFileName());
        Files.write(file, templateParam.getContent().getBytes());
        return RestResponse.ok();
    }

    @PostRoute("password")
    @JSON
    public RestResponse updatePassword(@Param PasswordParam passwordParam) {

        Option option = select().from(Option.class)
                .where(Option::getKey, Constant.SAFE_PASSWORD).one();

        String base64 = Base64.getEncoder().encodeToString(option.getValue().getBytes());

        boolean success = PasswordKit.checkPassword(passwordParam.getOldPass(),
                new String(Base64.getDecoder().decode(base64)));
        if (!success) {
            return RestResponse.fail("旧密码输入错误, 无法修改！");
        }
        String pass = PasswordKit.hashPassword(passwordParam.getNewPass());
        update().from(Option.class)
                .set(Option::getValue, pass)
                .where(Option::getKey, Constant.SAFE_PASSWORD).execute();
        return RestResponse.ok();
    }

    @PostRoute("theme/:name")
    @JSON
    public RestResponse updateTheme(@PathParam String name) {
        IndexController.THEME_NAME = name;
        update().from(Option.class)
                .set(Option::getValue, name)
                .where(Option::getKey, Constant.CURRENT_THEME).execute();
        return RestResponse.ok();
    }

    @PostRoute("config")
    @JSON
    public RestResponse updateConfig(@BodyParam InstallParam installParam) {
        installParam.setUpdate(true);
        makeMoneyService.updateConfig(installParam);
        Bootstrap.payApi = PayApi.getPayApi(installParam.getPlatform());
        return RestResponse.ok();
    }

    @PostRoute("black")
    @JSON
    public RestResponse updateBlackList(@BodyParam InstallParam installParam) {
        installParam.setUpdate(true);
        makeMoneyService.updateConfig(installParam);
        return RestResponse.ok();
    }

    @GetRoute("orders")
    public String orders(OrderParam orderParam, Request request) {
        Page<Order> orders = makeMoneyService.findOrders(orderParam);
        request.attribute("orderPage", orders);
        return "admin/orders.html";
    }

    @PostRoute("order/delete/:orderNo")
    @JSON
    public RestResponse orders(@PathParam String orderNo) {
        Anima.deleteById(Order.class, orderNo);
        return RestResponse.ok();
    }

    @GetRoute("logout")
    public void logout(Response response, Session session) {
        session.remove(Constant.LOGIN_SESSION_KEY);
        response.redirect("/");
    }

}
