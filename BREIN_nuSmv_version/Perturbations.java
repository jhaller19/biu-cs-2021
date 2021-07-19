
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Perturbations {
	/**
	 * TODO:
	 * Time step param
	 * ret file location generic
	 *
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		String modelFileName = args[0];
		String specFileName = args[1];
		String mode = args[2];
		String nPertubations = args[3];
		String targetNode = args[4];
		String typeOfPerturbationArg = args[5];
		String typeOfPerturbation = typeOfPerturbationArg.equals("KO") ? "Knockout" : "Overexpress";
		String timeStep = args[6];

		//parse model to get a list of nodes
		List<String> nodes = parseModelNodes(modelFileName);

		//Get text of spec file as template
		StringBuilder specFileTemplate = new StringBuilder();
		try (BufferedReader br = new BufferedReader(new FileReader(specFileName))) {
			String line;
			while ((line = br.readLine()) != null) {
				specFileTemplate.append(line + "\n");
			}
		}

		//Boot up NAE
		Runtime rt = Runtime.getRuntime();
		Process pr1 = rt.exec("javac NAE/*.java validate/*.java");
		/*BufferedReader reader=new BufferedReader(new InputStreamReader(
				pr1.getInputStream()));
		String line;
		while((line = reader.readLine()) != null) {
			System.out.println(line);
		}*/
		Process pr2 = rt.exec("jar cvfm NAE.jar NAE/manifest.txt NAE/*.class validate/*.class");
		/*BufferedReader reader1=new BufferedReader(new InputStreamReader(
				pr2.getInputStream()));
		String line1;
		while((line1 = reader1.readLine()) != null) {
			System.out.println(line1);
		}*/
		//Process pr3 = rt.exec("java -jar NAE.jar 1 src/main/java/TestModels/toy_model/model.net src/main/java/TestModels/toy_model/observations.spec time_step");
		boolean onFileSolutionsExist = false;
		boolean offFileSolutionsExist = false;
		StringBuilder out = new StringBuilder();

		if(nPertubations.equals("single")) {
			for (String node : nodes) {
				if (node.equals(targetNode)) continue;
				//Create spec file where target node is expected to be ON and run NAE
				//System.out.println("********************" + node + " " + typeOfPerturbation + "*********************\n");
				String onFile = createSpecFile(nodes, specFileTemplate, node, 1, typeOfPerturbationArg,
						targetNode, typeOfPerturbation, timeStep, modelFileName);
				//System.out.println("Expecting target [" + targetNode + "] to be ON...");
				onFileSolutionsExist = runNAE(onFile, modelFileName, rt, mode);

				//Create spec file where target node is expected to be OFF and run NAE
				String offFile = createSpecFile(nodes, specFileTemplate, node, 0, typeOfPerturbationArg,
						targetNode, typeOfPerturbation, timeStep, modelFileName);
				//System.out.println("Expecting target [" + targetNode + "] to be OFF...");
				offFileSolutionsExist = runNAE(offFile, modelFileName, rt, mode);
				processResults(out, onFileSolutionsExist, offFileSolutionsExist, node);
			}
		}else{
			for(String node1 : nodes){
				for(String node2 : nodes){
					if(node1.equals(targetNode) || node2.equals(targetNode) || node2.equals(node1)) continue;
						String onFile = createDoubleSpecFile(nodes, specFileTemplate, node1, node2, 1, typeOfPerturbationArg, targetNode,
								typeOfPerturbation, timeStep, modelFileName);
						onFileSolutionsExist = runNAE(onFile, modelFileName, rt, mode);
						String offFile = createDoubleSpecFile(nodes, specFileTemplate, node1, node2, 0, typeOfPerturbationArg,
								targetNode, typeOfPerturbation, timeStep, modelFileName);
						//System.out.println("Expecting target [" + targetNode + "] to be OFF...");
						offFileSolutionsExist = runNAE(offFile, modelFileName, rt, mode);
						processResults(out, onFileSolutionsExist, offFileSolutionsExist, node1 + " & " + node2);
				}
			}


		}
		System.out.println("***************************");
		System.out.println("Target Node: " + targetNode);
		System.out.println("Perturbation Type: " + typeOfPerturbation);
		System.out.println("Time Step: " + timeStep);
		System.out.println("***************************");
		System.out.println(out);

	}
	private static void processResults(StringBuilder out , boolean onFileSolutionsExist , boolean offFileSolutionsExist, String node){
		if(onFileSolutionsExist && !offFileSolutionsExist){
			out.append(node + ": On\n");
		}else if(offFileSolutionsExist && !onFileSolutionsExist){
			out.append(node + ": Off\n");
		}else{
			out.append(node + ": Inconclusive\n");
		}
	}

	private static boolean runNAE(String completeSpecFile, String modelFile, Runtime rt, String mode) throws IOException {
		Process pr = rt.exec("java -jar NAE.jar 100 " + modelFile + " " + completeSpecFile + " " + mode);
		BufferedReader reader=new BufferedReader(new InputStreamReader(
				pr.getInputStream()));
		/*String line;
		while((line = reader.readLine()) != null) {
			System.out.println(line);
		}*/

		return !(reader.readLine().equals("No Solutions Found"));
	}

	private static String createSpecFile(List<String> nodes , StringBuilder specFileTemplate, String curPerturbedNode,
										 int value, String typeOfPerturbationArg, String targetNode, String typeOfPerturbation,
										 String timeStep, String modelFileName) throws Exception {

		StringBuilder curSpecFile = new StringBuilder(specFileTemplate);
		curSpecFile.append("\n//Perturbations Specs");
		//Final result of target node
		curSpecFile.append("\n$" + typeOfPerturbation + "ResultExpression := {" + targetNode + " = "+ value + "};\n\n");
		//Set time steps
		curSpecFile.append("#" + curPerturbedNode + typeOfPerturbation + "Experiment[0] |= $" + curPerturbedNode + typeOfPerturbation + ";\n");
		curSpecFile.append("#" + curPerturbedNode + typeOfPerturbation + "Experiment["+ timeStep +"] |= $" + typeOfPerturbation + "ResultExpression;\n\n");
		//Actual pertubation on bottom
		curSpecFile.append("$" + curPerturbedNode+ typeOfPerturbation + ":=\n{\n");
		int nodeCount = 0;
		for (String node : nodes) {
			if (nodeCount != 0) {
				curSpecFile.append(" and ");
			}
			if (node.equals(curPerturbedNode)) {
				curSpecFile.append("  " + typeOfPerturbationArg + "(" + node + ")=1");
			} else {
				curSpecFile.append("  " + typeOfPerturbationArg + "(" + node + ")=0");
			}
			curSpecFile.append("\n");
			nodeCount++;
		}
		curSpecFile.append("};\n");
		//Create and write to new spec file (perturbations.spec)
		String path = null;
		try {
			path = modelFileName.substring(0, modelFileName.lastIndexOf("/"));
		}catch (StringIndexOutOfBoundsException e){
			path = modelFileName.substring(0, modelFileName.lastIndexOf("\\"));
		}
		String retFileName = path + File.separator + "perturbations.spec";
		PrintWriter pr = new PrintWriter(retFileName, "UTF-8");
		pr.print(curSpecFile.toString());
		pr.close();
		return retFileName;
	}

	private static String createDoubleSpecFile(List<String> nodes , StringBuilder specFileTemplate, String curPerturbedNode1, String curPerturbedNode2,
										 int value, String typeOfPerturbationArg, String targetNode, String typeOfPerturbation,
										 String timeStep, String modelFileName) throws Exception {
		StringBuilder curSpecFile = new StringBuilder(specFileTemplate);
		curSpecFile.append("\n//Perturbations Specs");
		//Final result
		curSpecFile.append("\n$" + typeOfPerturbation + "ResultExpression := {" + targetNode + " = "+ value + "};\n\n");
		//Set time step
		curSpecFile.append("#" + curPerturbedNode1 + "_" + curPerturbedNode2 + typeOfPerturbation + "Experiment[0] |= $" + curPerturbedNode1 + "_" + curPerturbedNode2 + typeOfPerturbation + ";\n");
		curSpecFile.append("#" + curPerturbedNode1 + "_" + curPerturbedNode2 + typeOfPerturbation + "Experiment["+ timeStep +"] |= $" + typeOfPerturbation + "ResultExpression;\n\n");
		//Actual pertubation on bottom
		curSpecFile.append("$" + curPerturbedNode1 + "_" + curPerturbedNode2 + typeOfPerturbation + ":=\n{\n");
		int nodeCount = 0;
		for (String node : nodes) {
			if (nodeCount != 0) {
				curSpecFile.append(" and ");
			}
			if (node.equals(curPerturbedNode1) || node.equals(curPerturbedNode2)) {
				curSpecFile.append("  " + typeOfPerturbationArg + "(" + node + ")=1");
			} else {
				curSpecFile.append("  " + typeOfPerturbationArg + "(" + node + ")=0");
			}
			curSpecFile.append("\n");
			nodeCount++;
		}
		curSpecFile.append("};\n");
		//Create and write to new spec file (perturbations.spec)
		String path = null;
		try {
			path = modelFileName.substring(0, modelFileName.lastIndexOf("/"));
		}catch (StringIndexOutOfBoundsException e){
			path = modelFileName.substring(0, modelFileName.lastIndexOf("\\"));
		}
		String retFileName = path + File.separator + "perturbations.spec";
		PrintWriter pr = new PrintWriter(retFileName, "UTF-8");
		pr.print(curSpecFile.toString());
		pr.close();
		return retFileName;
	}

	static List<String> parseModelNodes(String modelFileName) throws IOException {
		List<String> nodes = new ArrayList<>();
		BufferedReader model = readFile(modelFileName);
		//read through the file
		String line = null;
		//line number for error messages
		int lineNumber = 0;
		line = model.readLine();
		while(line.startsWith("directive")) line = model.readLine();
		String[] clauses = line.trim().split(";");
		for(String c:clauses){
			//if this line contains a "(" it must be a node declaration
			StringBuilder nameOfNode = new StringBuilder();
			if(c.contains("(")){
				c.trim();
				for(int i = 0; i < c.length();i++){
					if((c.charAt(i) == '[' ) ||(c.charAt(i)== '(')){
						break;
					}
					nameOfNode.append(c.charAt(i));
				}
			}
			nodes.add(nameOfNode.toString().trim());
		}
		return nodes;
	}
	//IO Helper Method
	static BufferedReader readFile(String file) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader( new FileInputStream(file),"UTF-8"));
		//get rid of BOM at beginning of file.
		in.mark(1);
		if (in.read() != 0xFEFF)
			in.reset();
		return in;
	}

}