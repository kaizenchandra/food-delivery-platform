package dev.saffron;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TransportPolicyTest {
 @Test void permitsSecureDeployment(){assertDoesNotThrow(()->TransportPolicy.validate("https://food.example","https://iam.example/realms/food","https://food.example/login/oauth2/code/keycloak",true,false));}
 @Test void rejectsHttpCallback(){assertThrows(IllegalStateException.class,()->TransportPolicy.validate("https://food.example","https://iam.example/realm","http://food.example/login",true,false));}
 @Test void rejectsInsecureCookie(){assertThrows(IllegalStateException.class,()->TransportPolicy.validate("https://food.example","https://iam.example/realm","https://food.example/login",false,false));}
 @Test void localCannotUsePublicUrls(){assertThrows(IllegalStateException.class,()->TransportPolicy.validate("http://food.example","http://localhost:8188/realm","http://localhost/login",false,true));}
 @Test void explicitLocalLoopbackWorks(){assertDoesNotThrow(()->TransportPolicy.validate("http://localhost:3008","http://localhost:8188/realm","http://localhost:8088/login",false,true));}
}
