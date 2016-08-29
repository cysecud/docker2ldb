package it.uniud.mads.docker2ldb;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;

public class Docker2Ldb {
    public static void main(String args[]) throws FileNotFoundException {
        InputStream input = new FileInputStream(new File("./etc/docker-compose.yml"));
        Yaml yaml = new Yaml();
        Map<String, Map> o = (Map<String, Map>) yaml.load(input);
        Map<String, Map> services = o.get("services");
        for (String service : services.keySet()) {
            System.out.println(services.get(service).keySet());
            System.out.println(services.get(service).values());
        }
        // System.out.println(services.keySet());
    }
}
