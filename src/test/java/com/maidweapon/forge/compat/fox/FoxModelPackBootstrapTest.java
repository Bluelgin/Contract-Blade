package com.maidweapon.forge.compat.fox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.zip.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class FoxModelPackBootstrapTest {
 @TempDir Path directory;
 private Path managed(){return directory.resolve("contract-blade-fox-1.0.0.zip");}
 private long backups()throws IOException{try(var list=Files.list(directory)){return list.filter(p->p.getFileName().toString().endsWith(".bak")).count();}}
 @Test void repeatedInstallDoesNotRewriteCurrentPackOrAccumulateBackups()throws Exception{
  FoxModelPackBootstrap.install(directory);byte[] original=Files.readAllBytes(managed());
  var time=FileTime.fromMillis(123456000);Files.setLastModifiedTime(managed(),time);
  FoxModelPackBootstrap.install(directory);
  assertArrayEquals(original,Files.readAllBytes(managed()));assertEquals(time,Files.getLastModifiedTime(managed()));assertEquals(0,backups());
 }
 @Test void editedManagedPackIsPreserved()throws Exception{
  FoxModelPackBootstrap.install(directory);
  try(var out=new ZipOutputStream(Files.newOutputStream(managed()))){
   out.putNextEntry(new ZipEntry("assets/contract_fox/maid_model.json"));
   out.write("{\"model_list\":[{\"model_id\":\"contract_fox:fox_white\"},{\"model_id\":\"contract_fox:fox_black\"}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));out.closeEntry();
   out.putNextEntry(new ZipEntry("USER_EDIT.txt"));out.write(new byte[]{1,2,3});out.closeEntry();
  }
  byte[] edited=Files.readAllBytes(managed());FoxModelPackBootstrap.install(directory);
  assertArrayEquals(edited,Files.readAllBytes(managed()));assertEquals(0,backups());
 }
}
