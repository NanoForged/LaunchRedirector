package io.github.nanoforged.launchredirector.mixin;


import io.github.nanoforged.launchredirector.LaunchRedirector;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;

@CTransformer(name = "com.fs.starfarer.StarfarerLauncher")
public class MixinStarfarerLauncher {

    @CInject(method = "Lcom/fs/starfarer/StarfarerLauncher;o00000(ZZLjava/lang/String;Ljava/lang/String;)V", target = @CTarget("HEAD"), cancellable = true)
    public static void Inject(boolean fs, boolean sound, String w, String h, final InjectionCallback ic){
        LaunchRedirector.relaunch(fs, sound, w, h);
        ic.setCancelled(true);
    }

}
