package com.github.cooldood.modules.impl.client;

import com.github.cooldood.modules.Category;
import com.github.cooldood.modules.Module;
import com.github.cooldood.modules.RegisterModule;

@RegisterModule(
        enabledByDefault = true,
        name = "PostProcessing",
        description = "Adds blur and bloom effects.",
        category = Category.CLIENT
)
public class PostProcessing extends Module {
    @Override
    protected void onEnable() { }

    @Override
    protected void onDisable() { }
}
