import java.io.*;
import org.jpc.jrsr.*;

public class UTFInputTest
{
    public static void main(String[] args) throws Exception
    {
        InputStream f = new FileInputStream(args[0]);
        UTFInputLineStream l = new UTFInputLineStream(f);
        String s;

        while((s = l.readLine()) != null) {
            System.err.println("Line: " + s);
        }
    }
}
