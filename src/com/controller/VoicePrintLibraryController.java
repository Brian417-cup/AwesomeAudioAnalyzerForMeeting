package com.controller;

import com.model.VoicePrint;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VoicePrintLibraryController {

    private static final String LIBRARY_FILE = "voice_print_library.dat";
    private Map<String, VoicePrint> voicePrintMap;
    private ObservableList<VoicePrint> voicePrintList;

    private static VoicePrintLibraryController instance;

    private VoicePrintLibraryController() {
        voicePrintMap = new HashMap<>();
        voicePrintList = FXCollections.observableArrayList();
        loadLibrary();
    }

    public static synchronized VoicePrintLibraryController getInstance() {
        if (instance == null) {
            instance = new VoicePrintLibraryController();
        }
        return instance;
    }

    public ObservableList<VoicePrint> getVoicePrintList() {
        return voicePrintList;
    }

    public List<VoicePrint> getVoicePrintsByUser(String userName) {
        return voicePrintList.stream()
                .filter(vp -> vp.getUserName().equals(userName))
                .collect(Collectors.toList());
    }

    public List<String> getAllUserNames() {
        return voicePrintList.stream()
                .map(VoicePrint::getUserName)
                .distinct()
                .collect(Collectors.toList());
    }

    public VoicePrint addVoicePrint(String userName, String voiceName, File audioFile) {
        VoicePrint voicePrint = new VoicePrint(userName, voiceName, audioFile);
        voicePrintMap.put(voicePrint.getId(), voicePrint);
        voicePrintList.add(voicePrint);
        saveLibrary();
        return voicePrint;
    }

    public boolean removeVoicePrint(String id) {
        VoicePrint removed = voicePrintMap.remove(id);
        if (removed != null) {
            voicePrintList.removeIf(vp -> vp.getId().equals(id));
            saveLibrary();
            return true;
        }
        return false;
    }

    public boolean updateVoicePrint(VoicePrint voicePrint) {
        if (voicePrintMap.containsKey(voicePrint.getId())) {
            voicePrintMap.put(voicePrint.getId(), voicePrint);
            int index = -1;
            for (int i = 0; i < voicePrintList.size(); i++) {
                if (voicePrintList.get(i).getId().equals(voicePrint.getId())) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) {
                voicePrintList.set(index, voicePrint);
            }
            saveLibrary();
            return true;
        }
        return false;
    }

    public void clearLibrary() {
        voicePrintMap.clear();
        voicePrintList.clear();
        saveLibrary();
    }

    private void saveLibrary() {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(
                    new FileOutputStream(LIBRARY_FILE)
            );
            List<VoicePrint> list = new ArrayList<>(voicePrintMap.values());
            oos.writeObject(list);
            oos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void loadLibrary() {
        try {
            File file = new File(LIBRARY_FILE);
            if (file.exists()) {
                ObjectInputStream ois = new ObjectInputStream(
                        new FileInputStream(file)
                );
                List<VoicePrint> list = (List<VoicePrint>) ois.readObject();
                ois.close();

                voicePrintMap.clear();
                voicePrintList.clear();

                for (VoicePrint vp : list) {
                    voicePrintMap.put(vp.getId(), vp);
                    voicePrintList.add(vp);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
