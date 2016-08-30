package it.uniud.mads.docker2ldb;

import it.uniud.mads.jlibbig.core.ldb.DirectedBigraph;
import it.uniud.mads.jlibbig.core.ldb.DirectedBigraphBuilder;
import it.uniud.mads.jlibbig.core.ldb.DirectedControl;
import it.uniud.mads.jlibbig.core.ldb.DirectedSignature;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Map;

public class Docker2Ldb {
    public static void main(String args[]) throws FileNotFoundException {
        DirectedControl container = new DirectedControl("container_1", true, 1, 1);
        DirectedControl[] controls = { container };
        DirectedSignature signature = new DirectedSignature(controls);
        DirectedBigraphBuilder bb = new DirectedBigraphBuilder(signature);
        System.out.println(bb);
        InputStream input = new FileInputStream(new File("./etc/docker-compose.yml"));
        Yaml yaml = new Yaml();
        Map<String, Map> o = (Map<String, Map>) yaml.load(input);
        Map<String, Map> services = o.get("services");
        for (String service : services.keySet()) { // parse every service in docker-compose file
            System.out.println(services.get(service).keySet());
            System.out.println(services.get(service).values());
        }
        // System.out.println(services.keySet());
    }
}
