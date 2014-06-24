import AssemblyKeys._ // put this at the top of the file

assemblySettings

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case "application.conf"     => MergeStrategy.discard
    case x => old(x)
  }
}

artifact in (Compile, assembly) ~= { art =>
  art.copy(`classifier` = Some("assembly"))
}

addArtifact(artifact in (Compile, assembly), assembly)
