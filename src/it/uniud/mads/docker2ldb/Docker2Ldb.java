package it.uniud.mads.docker2ldb;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class Docker2Ldb {
    public static void main(String args[]) throws FileNotFoundException {
        InputStream input = new FileInputStream(new File("./etc/docker-compose.yml"));
        Yaml yaml = new Yaml();
        Object data = yaml.load(input);
        System.out.println(data);
    }
}
