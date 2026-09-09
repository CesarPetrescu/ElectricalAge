/* Reports unsupported host graphics, never a passed client test. */
#include <OpenGL/OpenGL.h>
#include <OpenGL/gl3.h>
#include <stdio.h>
int main(void) {
    CGLRendererInfoObj info=0;GLint count=0;
    CGLError query=CGLQueryRendererInfo(0xffffffff,&info,&count);
    printf("{\"queryCode\":%d,\"renderers\":[",(int)query);
    for(int i=0;i<count;++i){
        GLint id=0,a=0,o=0;CGLDescribeRenderer(info,i,kCGLRPRendererID,&id);CGLDescribeRenderer(info,i,kCGLRPAccelerated,&a);CGLDescribeRenderer(info,i,kCGLRPOnline,&o);
        printf("%s{\"id\":%d,\"accelerated\":%d,\"online\":%d}",i?",":"",id,a,o);
    }
    if(info)CGLDestroyRendererInfo(info);
    printf("],\"profiles\":[");int ok=0;
    for(int i=0;i<3;++i){
        CGLPixelFormatAttribute profile=(CGLPixelFormatAttribute)(i==0?kCGLOGLPVersion_3_2_Core:i==1?kCGLOGLPVersion_4_1_Core:kCGLOGLPVersion_Legacy);
        CGLPixelFormatAttribute attrs[]={kCGLPFAOpenGLProfile,profile,kCGLPFAColorSize,(CGLPixelFormatAttribute)24,kCGLPFADepthSize,(CGLPixelFormatAttribute)24,(CGLPixelFormatAttribute)0};
        CGLPixelFormatObj pf=0;CGLContextObj ctx=0;GLint n=0;
        CGLError choose=CGLChoosePixelFormat(attrs,&pf,&n);CGLError create=pf?CGLCreateContext(pf,0,&ctx):kCGLBadPixelFormat;
        const char *v="",*r="";
        if(ctx){CGLSetCurrentContext(ctx);v=(const char*)glGetString(GL_VERSION);r=(const char*)glGetString(GL_RENDERER);if(i<2&&v)ok=1;}
        printf("%s{\"profile\":%d,\"chooseCode\":%d,\"createCode\":%d,\"version\":\"%s\",\"renderer\":\"%s\"}",i?",":"",profile,choose,create,v?v:"",r?r:"");
        if(ctx){CGLSetCurrentContext(0);CGLDestroyContext(ctx);}if(pf)CGLDestroyPixelFormat(pf);
    }
    printf("],\"minecraftCoreContext\":%s}\n",ok?"true":"false");return ok?0:42;
}
