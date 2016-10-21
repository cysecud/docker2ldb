package it.uniud.mads.docker2ldb;

import it.uniud.mads.jlibbig.core.ldb.*;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Docker2Ldb {

    public static void main(String args[]) {
        try {
            System.out.println("Composed bigraph: \n" + docker2ldb("./etc/docker-compose.yml"));
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    private static DirectedBigraph docker2ldb(String pathToYAML) throws Exception {
        // parsing yaml config file
        InputStream input = new FileInputStream(new File(pathToYAML));
        Yaml yaml = new Yaml();
        Map<String, Map> o = (Map<String, Map>) yaml.load(input);
        Map<String, Map> services = o.get("services");
        Map<String, Map> networks = o.get("networks");
        System.out.println("YAML config file correctly loaded.");

        boolean default_net = true; // used to know if networks are used
        int net_size = 1; // number of declared networks

        if (networks != null) {
            default_net = false;
            net_size = networks.size();
        }

        // preparing controls and empty bigraph
        List<DirectedControl> controls = new ArrayList<>();

        for (int i = 1; i <= net_size; i++) {
            controls.add(new DirectedControl("container_" + i, true, i, 1));
        }

        DirectedSignature signature = new DirectedSignature(controls);
        DirectedBigraphBuilder cmp = new DirectedBigraphBuilder(signature);

        Root r0 = cmp.addRoot(); // root 0
        System.out.println("Added a root to the bigraph.");

        // build the bigraph
        int locality = 1;
        List<DirectedBigraph> graphs = new ArrayList<>(services.size());
        Map<String, OuterName> net_names = new HashMap<>();

        // networks
        if (default_net) {
            net_names.put("default", cmp.addAscNameOuterInterface(1, "default"));
            System.out.println("Added default network.");
        } else {
            for (String net : networks.keySet()) {
                net_names.put(net, cmp.addAscNameOuterInterface(1, net));
                System.out.println("Added " + net + " network.");
            }
        }

        // save service outer names
        Map<String, OuterName> onames = new HashMap<>();

        for (String service : services.keySet()) {
            cmp.addSite(r0); // add a site
            System.out.println("Added a site to the bigraph.");
            if (default_net) {
                cmp.addAscNameInnerInterface(locality, "default", net_names.get("default")); // add default net
            }
            onames.put(service, cmp.addDescNameInnerInterface(locality, service));
            cmp.addDescNameOuterInterface(1, service, onames.get(service)); // expose the name
            locality++;
        }

        locality = 1;
        for (String service : services.keySet()) { // parse every service in docker-compose file
            System.out.println("Service: " + service);
            List<String> current_nets = (List<String>) services.get(service).get("networks");
            List<String> ports = (List<String>) services.get(service).get("expose");
            List<String> mappings = (List<String>) services.get(service).get("ports");
            List<String> links = (List<String>) services.get(service).get("links");

            DirectedBigraphBuilder current = new DirectedBigraphBuilder(signature);
            System.out.println("Creating a bigraph for the service.");
            Root currentRoot = current.addRoot(); // add a root
            Node node;
            if (default_net) {
                node = current.addNode("container_1", currentRoot); // add a node of container type
            } else {
                if (current_nets != null) {
                    node = current.addNode("container_" + current_nets.size(), currentRoot); // add a node of container type with the correct number of networks
                } else {
                    throw new Exception("You must declare service networks, because you declared global networks!");
                }
            }

            current.addSite(node); // add a site for future purposes
            current.addDescNameOuterInterface(1, service, node.getInPort(0).getEditable());

            // networks
            if (default_net) {
                System.out.println("Service connects to network default, adding it to the interface.");
                node.getOutPort(0).getEditable().setHandle(current.addAscNameOuterInterface(1, "default").getEditable()); // link the net to the node
            } else {
                int i = 0;
                // local_nets cannot be null because previous exception was skipped
                for (String network : current_nets) {
                    if (!networks.keySet().contains(network)) {
                        throw new Exception("Network " + network + " not declared.");
                    }
                    System.out.println("Service connects to network " + network + ", adding it to the interface.");
                    node.getOutPort(i).getEditable().setHandle(current.addAscNameOuterInterface(1, network).getEditable()); // link the net to the node
                    cmp.addAscNameInnerInterface(locality, network, net_names.get(network));
                    i++;
                }
            }
            // expose
            if (ports != null) {
                for (String port : ports) {
                    System.out.println("Service exposes port " + port + ", adding it to the interface.");
                    current.addDescNameOuterInterface(1, port, current.addDescNameInnerInterface(1, port));
                    cmp.addDescNameInnerInterface(locality, port);
                }
            }
            // ports
            if (mappings != null) {
                for (String map : mappings) {
                    String[] ps = map.split(":");
                    System.out.println("Service maps port " + ps[1] + " to port " + ps[0] + ", adding them to interfaces.");
                    current.addDescNameOuterInterface(1, ps[1], current.addDescNameInnerInterface(1, ps[1]));
                    cmp.addDescNameOuterInterface(1, ps[0], cmp.addDescNameInnerInterface(locality, ps[1]));
                }
            }
            // links
            if (links != null) {
                for (String link : links) {
                    String[] ls = link.split(":");
                    if (ls.length > 1) {
                        System.out.println("Service links to container " + ls[0] + ", renaming it to " + ls[1] + " recreating this on interfaces.");
                        current.addAscNameInnerInterface(1, "l_" + ls[1] + "_" + service, current.addAscNameOuterInterface(1, "l_" + ls[1] + "_" + service));
                        cmp.addAscNameInnerInterface(locality, "l_" + ls[1] + "_" + service, onames.get(ls[0]));
                    } else {
                        System.out.println("Service links to container " + ls[0] + ", recreating this on interfaces.");
                        current.addAscNameInnerInterface(1, "l_" + ls[0] + "_" + service, current.addAscNameOuterInterface(1, "l_" + ls[0] + "_" + service));
                        cmp.addAscNameInnerInterface(locality, "l_" + ls[0] + "_" + service, onames.get(ls[0]));
                    }
                }
            }
            System.out.println("Resulting bigraph: \n" + current);
            System.out.println("----------------------------------------------");
            graphs.add(current.makeBigraph());
            locality++; // ready for the next
        }
        System.out.println("Compose bigraph: \n" + cmp);
        System.out.println("----------------------------------------------");

        List<DirectedBigraph> outs = new ArrayList<>();
        outs.add(cmp.makeBigraph());

        return DirectedBigraph.compose(outs, graphs);
    }
}
