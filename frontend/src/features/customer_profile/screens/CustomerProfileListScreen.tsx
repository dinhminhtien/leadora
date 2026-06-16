"use client";

import React, { useState, useMemo } from "react";
import { Search, Users, Mail, Phone, Building2, UserPlus, FileText } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
export type CustomerProfile = {
  id: string;
  name: string;
  email: string;
  phone: string;
  company: string;
  lastInteractionDate: string;
  totalDealValue: number;
  notes: string;
};

export function CustomerProfileListScreen() {
  const [contacts, setContacts] = useState<CustomerProfile[]>([]);
  const [search, setSearch] = useState("");

  const filteredContacts = useMemo(() => {
    return contacts.filter(
      c =>
        c.name.toLowerCase().includes(search.toLowerCase()) ||
        c.email.toLowerCase().includes(search.toLowerCase()) ||
        c.company.toLowerCase().includes(search.toLowerCase())
    );
  }, [contacts, search]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Guest Profiles</h1>
          <p className="text-xs text-slate-400">Manage guest lists, company corporate directories, and past deal metrics</p>
        </div>
        <Button variant="primary" size="sm" className="gap-1 bg-blue-600 hover:bg-blue-700 text-xs font-semibold text-white">
          <UserPlus className="size-3.5" />
          <span>Add Guest Profile</span>
        </Button>
      </div>

      <Card className="border-slate-100 shadow-sm bg-white">
        <CardContent className="py-3 px-4">
          <div className="relative w-full md:w-72">
            <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search guests, email, corporate..."
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition"
            />
          </div>
        </CardContent>
      </Card>

      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        <Table>
          <TableHeader className="bg-slate-50 border-b border-slate-100 text-slate-500">
            <TableRow hoverable={false}>
              <TableHead className="font-semibold text-xs text-slate-500">Guest Name</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Email Address</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Phone</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Company</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Last Interaction</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Total Booking Value</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Internal Notes</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredContacts.length > 0 ? (
              filteredContacts.map(contact => (
                <TableRow key={contact.id} className="hover:bg-slate-50/70 border-b border-slate-100 transition">
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-800">
                    <div className="flex items-center gap-2">
                      <span className="size-6 rounded-full bg-blue-100 text-blue-700 text-[10px] font-bold flex items-center justify-center">
                        {contact.name.slice(0, 2).toUpperCase()}
                      </span>
                      {contact.name}
                    </div>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-blue-600 hover:underline">
                    {contact.email}
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600 font-semibold">{contact.phone}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600">{contact.company}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-500">{contact.lastInteractionDate}</TableCell>
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-800">
                    ${contact.totalDealValue.toLocaleString()}
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-400 italic truncate max-w-xs">
                    {contact.notes}
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow hoverable={false}>
                <TableCell colSpan={7} className="py-8 text-center text-slate-400 text-xs">
                  No guest profile records matched your filters.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
