# OpenMYJ2C

## The bases used in this obfuscation:
[native-obfuscator](https://github.com/radioegor146/native-obfuscator)

#### Open Source Information
This open source version is the official open source, open source the old version, we will continue to update the paid version! The open source version can be compiled and used directly.

#### 介绍
OpenMYJ2C将编译的Java的Class字节码转换为C语言代码。交叉编译（您不用自己配置编译环境，OpenMYJ2C自动完成）可以生成Windows，Linux，Mac系统X86，ARM平台的动态链接库文件后，通过Java Native Interface 重新链接到原始程序。在此过程结束时，包含原始方法的.class文件的字节码中不会保留原始方法的信息。 



编译前
```
public class App {
	public static void main(String args[]) {
		System.out.println("Hello, world!");
	}
}
```
编译后

```
public class App {
	public static native void main(String args[]);
}
```

#### 使用说明

 _运行默认配置_

1.  在命令行切换到OpenMYJ2C.jar所在目录，运行java -jar OpenMYJ2C.jar 待编译jar路径 输出目录或文件路径
```bash
java -jar OpenMYJ2C.jar D:\dev\SnakeGame.jar D:\dev\SnakeGame 
```
2.  自定义配置
```xml
<OpenMYJ2C>
	<targets><!--需要编译动态链接库类型，根据自己的程序运行环境配置，每种配置会编译一个动态链接库，不需要的平台建议不要配置会增加jar包大小-->
		<target>WINDOWS_X86_64</target>
		<target>WINDOWS_AARCH64</target>
		<target>MACOS_X86_64</target>
		<target>MACOS_AARCH64</target>
		<target>LINUX_X86_64</target>
		<target>LINUX_AARCH64</target>
	</targets>
	<include>
		<match className="demo/abc/**" /><!--需要编译的类-->
	</include>
	<exclude>
		<match className="demo/Main" /><!--不要编译的类-->
	</exclude>
</OpenMYJ2C>
```

目前可以编译WINDOWS，LINUX，MACOS系统X86_64和ARM架构动态链接库，具体配置如下：

```bash
    WINDOWS_X86_64
    WINDOWS_AARCH64
    MACOS_X86_64
    MACOS_AARCH64
    LINUX_X86_64
    LINUX_AARCH64
```

 **注意** 
```bash
OpenMYJ2C的代码将比直接在JVM上运行的代码运行得慢得多。这是由于无法避免的本机函数调用的固有开销。
建议您只使用OpenMYJ2C来混淆应用程序中对性能不重要的敏感部分。要合理利用include，exclude配置要混淆方法。
```

关于include

白名单，如果配置白名单，则只会编译白名单匹配到的类和方法

关于exclude

黑名单，配置黑名单，则排除掉黑名单匹配的方法，如果不配置白名单，则匹配排除黑名单外的所有类和方法

关于match

```bash
<match className="demo/abc/*" methodName="main" methodDesc="(\[Ljava/lang/String;)V" />
```

其中

```bash
className 匹配类名，如果只配置className 则会匹配该类的全部方法，该属性支持demo/abc/*或demo.abc.*
methodName 匹配类的方法名，设置该属性则只匹配到类的方法，未匹配的不包含
methodDesc 匹配JVM的方法描述，相同方法名称，只匹配到对应方法描述方法
```

JVM 方法描述
```bash
方法描述格式：(parameterTypes)returnType 类型如下:

    V is the void type, used only for the return value of a method. If a method takes no parameters, parameterTypes should be left blank
    I is the primitive integer type
    J is the primitive long type
    S is the primitive short type
    F is the primitive float type
    D is the primitive integer type
    C is the primitive char type
    B is the primitive byte type
    Z is the primitive boolean type
    Ljava/lang/Object; is the fully qualified class java.lang.Object
    [elementType is an array of elementType. elementType may itself be an array for multidimensional arrays.

Note that [ is a regex special character and needs to be escaped with a \

For example:
Method Signature 	JVM Method Descriptor
void main(String[] args) 	([Ljava/lang/String;)V
String toString() 	()Ljava/lang/String;
void wait(long t, int n) 	(JI)V
boolean compute(int[][] k) 	([[I)Z
```
 _运行自定义配置_ 

在命令行执行如下命令

```bash
java -jar OpenMYJ2C.jar D:\dev\SnakeGame.jar D:\dev\SnakeGame -c config1.xml
```

 _运行注解配置_

1.  请在源码上增加Native和NotNative注解后执行

2.  在命令行执行如下命令

```bash
java -jar OpenMYJ2C.jar D:\dev\SnakeGame.jar D:\dev\SnakeGame -a
```

#### 注意事项

     较旧的Java编译器可能会发出JSR和RET指令，这是Java 7字节码或更新版本中不允许的弃用指令。OpenMYJ2C仅支持Java8字节码及以上版本，因此无法处理JSR/RET指令。如果您的应用程序中有包含Java 6类文件的较旧库，则应将它们排除。 
    Java 11中允许一个称为ConstantDynamic的新特性，它允许在运行时通过引导方法动态初始化常量池条目。包含ConstantDynamic条目的类文件目前与OpenMYJ2C不兼容。应将它们排除。

    JNI接口的限制限制了任何转换方法的性能。特别是，与Java相比，方法调用、字段访问和数组操作速度较慢。在JNI代码中，算术、强制转换、控制流和局部变量访问仍然非常快（甚至可以通过C编译器进行优化），在一些要求性能的方法应将他们排除

#### 编写安全的代码

OpenMYJ2C是一个强大的混淆器，但其有效性可能会受到其翻译的代码的限制。考虑以下几点：

不安全的写法

```bash
public class App {
	public static void main(String[] args) {
		if(!checkLicence()) {
			System.err.println("Invalid licence");
			return;
		}
		initApp();
		runApp();
	}

	private static native boolean checkLicence(); // Protected by OpenMYJ2C

	private static native void initApp(); // Protected by OpenMYJ2C

	private static native void runApp(); // Protected by OpenMYJ2C
}
```

在此示例中，即使checkLicence代码受OpenMYJ2C保护，攻击者也很容易修改主方法，直接返回 true 达到破解目的。

```bash
public class App {
	public static void main(String[] args) {
		if(!checkLicence()) {
			System.err.println("Invalid licence");
			return;
		}
		initApp();
		runApp();
	}

	private static boolean checkLicence() {
	    return true; //这样就可以达到破解目的，绕过正常授权验证方法
	}

	private static native void initApp(); // Protected by OpenMYJ2C

	private static native void runApp(); // Protected by OpenMYJ2C
}
```

比较好的写法

```bash
public class App {
	public static void main(String[] args) {
		checkLicenceAndInitApp();
		runApp();
	}

	private static native void checkLicenceAndInitApp(); // Protected by OpenMYJ2C
}
```


在这里，攻击者即使修改CheckLicensandInApp的方法，跳过授权部分但是也不知道里面要执行哪些初始化功能，修改之后应用程序将无法正常运行（因为它将无法初始化）。

推荐使用OpenMYJ2C保护应用程序的初始化代码，因为它只在运行的时候执行一次，初始化方法不会反复被执行不用担心有性能问题。 


#### 授权信息

1.免费版 只能编译一个类中的一个方法，可以永久免费使用

2.试用版 免费使用，编译后的jar包可以免费使用一周（7天），之后程序将无法使用

3.个人版 可按月按年付费，控制台会打印OpenMYJ2C的编译信息，控制台信息可定制，不能去除，同时有编译数量限制

4.专业版 可按月按年付费，控制台无任何信息输出，没有编译数量限制

#### 常见问题

1.  无法编译动态链接库

    答：可能是路径中有中文或特殊字符，导致zig无法编译，修改路径放到非中文或特殊字符的路径下执行编译

2.  需要编译全平台吗

    答：默认配置是编译windows，linux，mac系统支持64位和arm平台，您可以根据自己的需求配置编译的平台

3.  OpenMYJ2C会对我的应用程序的性能产生重大影响吗？

    答：许多代码保护工具必须在性能和安全性之间进行权衡。我们建议您仅在敏感代码或性能不重要的代码上使用它。

4.  OpenMYJ2C是否支持lambdas/streams/exceptions/threads/locks/。。。？

    答：OpenMYJ2C对编译的Java字节码进行操作，支持Java 8或更高版本的JVM编译的任何字节码。OpenMYJ2C支持Java中的所有语言特性，并且还支持在JVM上运行的其他编程语言，如                kotlin。

5.  与自己编写JNI方法相比，OpenMYJ2C有哪些优势？

    答：编写使用Java本机接口的代码非常困难，而且这种代码通常更难调试。OpenMYJ2C允许您编写（和测试！）Java代码，请使用Java中的所有代码。此外：
        OpenMYJ2C可以翻译Java混淆器的输出，如Zelix、Klassmaster或Stringer。
        OpenMYJ2C可以在Java API中转换在C中没有直接等价的东西，如lambdas、方法引用和流。
        使用OpenMYJ2C，您不需要知道如何使用JNI或C，也不需要编写在运行时将本机库链接到应用程序的代码（OpenMYJ2C自动注入）。
        OpenMYJ2C可以翻译现有的Java代码——您不需要浪费时间重写已经完成的应用程序部分。

6.  在使用OpenMYJ2C混淆之前，我可以对它们应用额外的混淆处理吗？

    答：当然，这是可能的，尽管我们不能保证任何代码混淆工具的兼容性。此外，如果在运行OpenMYJ2C之前已经使用了Java混淆工具，则进一步混淆文件可能是不必要的。
 
7.  在运行OpenMYJ2C之后，我可以对输出JAR文件应用额外的混淆处理吗？

    答：对已用OpenMYJ2C混淆的任何方法/字段/类使用名称混淆将导致运行时由于链接不满足而崩溃。您可以自由使用字符串混淆、引用混淆、资源加密等。

8.  运行报java.security.InvalidKeyException: Illegal key size异常
 
    答：替换jdk/jre/lib/security目录中的local_policy.jar和US_export_policy.jar文件，文件在jce_policy-8.zip中，下载解压即可获取到以上文件

9.  联系方式

    答：请加QQ群：197453088

